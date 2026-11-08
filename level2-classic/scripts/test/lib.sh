#!/bin/bash
# 시험 도우미: 확인 함수, 임시 작업 폴더, 가짜 서비스 기동. /bin/bash 3.2 호환 (연관 배열 없음).
# 임시 작업은 저장소 안 .local-run/test-work/ 에서만 한다 (/tmp 사용 금지).

TEST_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS_DIR="$(cd "$TEST_LIB_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$SCRIPTS_DIR/.." && pwd)"
FAKES_DIR="$TEST_LIB_DIR/fakes"
RUN_LOCAL="$SCRIPTS_DIR/run-local.sh"
# bash 3.2 는 xtrace 접두사(PS4 확장 결과)를 99자에서 자른다. 줄 번호를 잃지 않도록 작업 폴더(.local-run/test-work/<이름>)에서
# 짧은 상대 경로로 부른다 (BASH_SOURCE 가 짧아진다). 작업 폴더는 항상 프로젝트 아래 세 단계다.
RL_REL="../../../scripts/run-local.sh"
FIXTURES_REL="../../../scripts/test/fixtures"
TEST_WORK_BASE="$PROJECT_DIR/.local-run/test-work"
# 호스트 부하가 높을 수 있어 기다림 상한은 넉넉하게 두고 환경 변수로 바꾼다.
T_WAIT="${T_WAIT:-120}"
# set -u 아래의 eval/-c 문자열에는 BASH_SOURCE 가 없어 ${BASH_SOURCE} 만으로는 셸이 죽는다: 기본값을 둔다
COV_PS4='+TRACE|${BASH_SOURCE:-}|${LINENO:-0}|'
SUITE_NAME=""
RESULTS_FILE=""
CASE_NAME=""
LAUNCH_N=0

# ---------- 시험 묶음과 확인 ----------

suite_begin() {
  SUITE_NAME="$1"
  mkdir -p "$TEST_WORK_BASE"
  printf '*\n' > "$PROJECT_DIR/.local-run/.gitignore"
  RESULTS_FILE="$TEST_WORK_BASE/results-$SUITE_NAME-$$.txt"
  : > "$RESULTS_FILE"
}

run_case() {
  local fn="$1"
  # RUN_ONLY=<정규식> 이면 이름이 맞는 시험만 실행한다 (반복 개발용)
  if [ -n "${RUN_ONLY:-}" ] && ! printf '%s' "$fn" | grep -q -E "$RUN_ONLY"; then
    return 0
  fi
  printf 'CASE %s\n' "$fn" >> "$RESULTS_FILE"
  printf '\n== %s\n' "$fn"
  ( CASE_NAME="$fn"; WORK=""; "$fn"; if [ -n "$WORK" ]; then end_scenario; fi )
  local n
  n="$(grep -c -E "^(PASS|FAIL) $fn " "$RESULTS_FILE" || true)"
  if [ "$n" = "0" ]; then
    printf 'FAIL %s 확인이 하나도 실행되지 않았습니다\n' "$fn" | tee -a "$RESULTS_FILE"
  fi
}

ok() {
  printf 'PASS %s %s\n' "$CASE_NAME" "$1" >> "$RESULTS_FILE"
  printf '  ok   %s\n' "$1"
  return 0
}

bad() {
  printf 'FAIL %s %s\n' "$CASE_NAME" "$1" >> "$RESULTS_FILE"
  printf '  FAIL %s\n' "$1"
  if [ -n "${2:-}" ]; then
    printf '       %s\n' "$2"
  fi
  return 0
}

assert_eq() {
  if [ "$1" = "$2" ]; then ok "$3"; else bad "$3" "기대 '$2', 실제 '$1'"; fi
  return 0
}

assert_contains() {
  case "$1" in
    *"$2"*) ok "$3" ;;
    *) bad "$3" "'$2' 이(가) 없음. 실제: $(printf '%s' "$1" | head -n 8 | cut -c1-300)" ;;
  esac
  return 0
}

assert_not_contains() {
  case "$1" in
    *"$2"*) bad "$3" "'$2' 이(가) 있으면 안 됨" ;;
    *) ok "$3" ;;
  esac
  return 0
}

assert_file_has() {
  if grep -q -F -- "$2" "$1" 2>/dev/null; then ok "$3"; else bad "$3" "$1 에 '$2' 없음"; fi
  return 0
}

assert_file_lacks() {
  if grep -q -F -- "$2" "$1" 2>/dev/null; then bad "$3" "$1 에 '$2' 있으면 안 됨"; else ok "$3"; fi
  return 0
}

# 비밀 값 비출력 확인은 xtrace 실행(COVERAGE=1)에서는 건너뛴다 (G2).
secret_checks_enabled() {
  [ "${COVERAGE:-0}" != "1" ]
}

assert_true() {
  local desc="$1"
  shift
  if "$@"; then ok "$desc"; else bad "$desc" "명령 실패: $*"; fi
  return 0
}

assert_false() {
  local desc="$1"
  shift
  if "$@"; then bad "$desc" "명령이 성공하면 안 됨: $*"; else ok "$desc"; fi
  return 0
}

suite_end() {
  local cases passed failed
  cases="$(grep -c '^CASE ' "$RESULTS_FILE" || true)"
  passed="$(grep -c '^PASS ' "$RESULTS_FILE" || true)"
  failed="$(grep -c '^FAIL ' "$RESULTS_FILE" || true)"
  printf '\nRESULT %s: %s cases, %s assertions passed, %s failed\n' "$SUITE_NAME" "$cases" "$passed" "$failed"
  if [ "$failed" != "0" ]; then
    grep '^FAIL ' "$RESULTS_FILE" | sed 's/^/  /'
    return 1
  fi
  return 0
}

# ---------- 기다림 ----------

wait_until() {
  local limit="$1"
  shift
  local start="$SECONDS"
  while ! "$@"; do
    if [ $((SECONDS - start)) -ge "$limit" ]; then
      return 1
    fi
    sleep 0.2
  done
  return 0
}

file_has() {
  grep -q -F -- "$2" "$1" 2>/dev/null
}

pid_alive() {
  kill -0 "$1" 2>/dev/null
}

pid_gone() {
  ! kill -0 "$1" 2>/dev/null
}

port_open() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t >/dev/null 2>&1
}

port_closed() {
  ! port_open "$1"
}

# ---------- 작업 폴더와 가짜 서비스 ----------

pick_ports() {
  local out
  out="$(node -e "
const net = require('net');
const servers = [0, 1, 2, 3, 4].map(() => net.createServer());
let ports = [];
let pending = servers.length;
servers.forEach((s) => s.listen(0, '127.0.0.1', () => {
  ports.push(s.address().port);
  pending -= 1;
  if (pending === 0) {
    console.log(ports.join(' '));
    servers.forEach((x) => x.close());
  }
}));
")"
  set -- $out
  P1="$1"
  P2="$2"
  P3="$3"
  P4="$4"
  P5="$5"
}

write_wrapper() {
  printf '#!/bin/bash\nexec /bin/bash "%s" %s "$@"\n' "$FAKES_DIR/fake-service.sh" "$2" > "$1"
  chmod +x "$1"
}

# 서비스를 띄우지 않는 함수 단위 시험용 작업 폴더 (포트를 고르지 않아 빠르다)
new_light() {
  WORK="$TEST_WORK_BASE/$SUITE_NAME-$1"
  rm -rf "$WORK"
  mkdir -p "$WORK/state" "$WORK/fake-bin"
  clean_env
  export FAKE_STATE_DIR="$WORK/state"
  export RUN_LOCAL_ROOT="$WORK/root"
}

# 사용자 셸에 있을 수 있는 설정 변수를 지운다
clean_env() {
  unset U1_PORT U2_PORT U3_PORT RUN_LOCAL_UI_PORT SERVER_ADDRESS UI_HOST UI_ALLOW_NON_LOOPBACK
  unset PAYMENT_WEBHOOK_SECRET RUN_LOCAL_READY_TIMEOUT RUN_LOCAL_POLL_INTERVAL
  unset RUN_LOCAL_WATCH_INTERVAL RUN_LOCAL_TERM_GRACE RUN_LOCAL_ROOT
  unset SEARCH_SERVICE_URL RESERVATION_SERVICE_URL WEBHOOK_URL U1_URL U2_URL U3_URL SHELLOPTS_TEST
}

new_scenario() {
  WORK="$TEST_WORK_BASE/$SUITE_NAME-$1"
  rm -rf "$WORK"
  clean_env
  mkdir -p "$WORK/root/services/u1-search-service/target/classes"
  mkdir -p "$WORK/root/services/u2-reservation-service/target/classes"
  mkdir -p "$WORK/root/services/u3-payment-service/target/classes"
  mkdir -p "$WORK/root/services/u4-web-ui/node_modules"
  mkdir -p "$WORK/state" "$WORK/fake-bin"
  write_wrapper "$WORK/fake-bin/mvn" mvn
  write_wrapper "$WORK/fake-bin/npm" npm
  write_wrapper "$WORK/fake-bin/docker" docker
  write_wrapper "$WORK/fake-bin/docker-compose" docker-compose
  pick_ports
  export FAKE_STATE_DIR="$WORK/state"
  export RUN_LOCAL_ROOT="$WORK/root"
  export RUN_LOCAL_UI_PORT="$P4"
  export FAKE_U4_PORT="$P4"
  export U1_PORT="$P1"
  export U2_PORT="$P2"
  export U3_PORT="$P3"
  export PATH="$WORK/fake-bin:$PATH"
  export RUN_LOCAL_READY_TIMEOUT="${RUN_LOCAL_READY_TIMEOUT:-60}"
  export RUN_LOCAL_POLL_INTERVAL="${RUN_LOCAL_POLL_INTERVAL:-0.5}"
  export RUN_LOCAL_WATCH_INTERVAL="${RUN_LOCAL_WATCH_INTERVAL:-1}"
  export RUN_LOCAL_TERM_GRACE="${RUN_LOCAL_TERM_GRACE:-10}"
}

end_scenario() {
  kill_leftovers
  if [ "${COVERAGE:-0}" = "1" ] && [ -n "${COVERAGE_TRACE_DIR:-}" ]; then
    local f
    for f in "$WORK"/err-*; do
      [ -f "$f" ] || continue
      cp "$f" "$COVERAGE_TRACE_DIR/$SUITE_NAME-$(basename "$WORK")-$(basename "$f")"
    done
  fi
  if [ "${KEEP_WORK:-0}" != "1" ]; then
    rm -rf "$WORK"
  fi
}

kill_leftovers() {
  local f p
  for f in "$WORK"/state/*.pids "$WORK"/state/*.pid; do
    [ -f "$f" ] || continue
    while read -r p; do
      if [ -n "$p" ]; then
        kill -9 "$p" 2>/dev/null || true
      fi
    done < "$f"
  done
  if [ -n "${RL_PID:-}" ]; then
    kill -9 -- "-$RL_PID" 2>/dev/null || true
    kill -9 "$RL_PID" 2>/dev/null || true
  fi
  return 0
}

all_service_pids() {
  local f
  for f in "$WORK"/state/*.pids; do
    [ -f "$f" ] || continue
    cat "$f"
  done
}

all_pids_gone() {
  local p
  for p in $(all_service_pids); do
    if kill -0 "$p" 2>/dev/null; then
      return 1
    fi
  done
  return 0
}

all_ports_closed() {
  local p
  for p in "$P1" "$P2" "$P3" "$P4"; do
    if port_open "$p"; then
      return 1
    fi
  done
  return 0
}

start_listener() {
  node -e "require('net').createServer().listen(Number(process.argv[1]), '127.0.0.1', () => {})" "$1" &
  echo $! >> "$WORK/state/listener.pid"
  wait_until 30 port_open "$1"
}

# ---------- run-local.sh 실행 ----------

# 호출마다 겹치지 않는 출력·오류(xtrace) 파일을 만든다. $(...) 안에서 불러도 서로 덮어쓰지 않도록 mktemp 를 쓴다.
new_capture_files() {
  RL_OUT="$(mktemp "$WORK/out-XXXXXX")"
  RL_ERR="$(mktemp "$WORK/err-XXXXXX")"
}

# run-local.sh 에 주는 환경. 일반 실행은 SHELLOPTS 와 PS4 가 서비스로 새는지 보려고 값을 일부러 내보낸다.
# COVERAGE=1 이면 SHELLOPTS=xtrace 로 줄 실행을 기록한다 (G2).
build_rl_env() {
  RL_ENV=("RUN_LOCAL_TEST=1")
  if [ "${COVERAGE:-0}" = "1" ]; then
    RL_ENV=("${RL_ENV[@]}" "SHELLOPTS=xtrace" "PS4=$COV_PS4")
  else
    RL_ENV=("${RL_ENV[@]}" "SHELLOPTS=noclobber" "PS4=TESTPS4> ")
  fi
}

# 백그라운드 실행. set -m 으로 작업 제어를 켜서 INT 를 무시하지 않는 그룹으로 띄운다.
launch_run_local() {
  set -m
  build_rl_env
  new_capture_files
  ( cd "$WORK" && exec env "${RL_ENV[@]}" /bin/bash "$RL_REL" "$@" > "$RL_OUT" 2> "$RL_ERR" ) &
  RL_PID=$!
}

# 끝날 때까지 기다린다. RL_RC 에 종료 코드를 담는다.
wait_run_local() {
  RL_RC=0
  if wait_until "$T_WAIT" pid_gone "$RL_PID"; then
    wait "$RL_PID" 2>/dev/null || RL_RC=$?
  else
    kill -9 "$RL_PID" 2>/dev/null || true
    RL_RC=124
  fi
}

# 출력에 문구가 나올 때까지 기다린다 (화면 출력 기준)
wait_for_text() {
  wait_until "${2:-$T_WAIT}" rl_has "$1"
}

# 동기 실행. 출력은 RL_OUT/RL_ERR, 종료 코드는 RL_RC.
run_rl_sync() {
  launch_run_local "$@"
  wait_run_local
}

# 화면 출력(표준 출력 + 표준 오류에서 xtrace 줄을 뺀 것)
rl_text() {
  cat "$RL_OUT" 2>/dev/null || true
  grep -v 'TRACE|' "$RL_ERR" 2>/dev/null || true
}

rl_has() {
  rl_text | grep -q -F -- "$1"
}

# run-local.sh 를 source 해 함수를 직접 부른다. 셸 조각은 $1, 환경은 호출 쪽 export.
# 결과: RL_OUT, RL_ERR, RL_RC
rl_eval() {
  build_rl_env
  new_capture_files
  RL_RC=0
  ( cd "$WORK" && exec env "${RL_ENV[@]}" /bin/bash -c '. "$1"; eval "$2"' rl "$RL_REL" "$1" > "$RL_OUT" 2> "$RL_ERR" ) || RL_RC=$?
}

# 서비스 환경 기록에서 변수 값을 읽는다.
svc_env_value() {
  grep "^$2=" "$WORK/state/$1.env" 2>/dev/null | head -n 1 | cut -d= -f2-
}

svc_env_has() {
  grep -q "^$2=" "$WORK/state/$1.env" 2>/dev/null
}

svc_started() {
  [ -f "$WORK/state/$1.pids" ] && [ "$(grep -c . "$WORK/state/$1.pids")" -ge 3 ]
}

#!/usr/bin/env bash
set -euo pipefail

# ===== 설정 구역 (기동 순서, 포트, 제한 시간의 기본값은 이 구역 한곳에만 둔다) =====
DEFAULT_U1_PORT=8081
DEFAULT_U2_PORT=8082
DEFAULT_U3_PORT=8083
DEFAULT_UI_PORT=5173
LOOPBACK=127.0.0.1
DEFAULT_READY_TIMEOUT=90
DEFAULT_POLL_INTERVAL=1
DEFAULT_WATCH_INTERVAL=2
DEFAULT_TERM_GRACE=10
SERVICE_ORDER="u1 u2 u3 u4"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRET_MIN_USER=32
SECRET_MIN_GENERATED=64
# 서비스를 띄울 때 먼저 지우는 환경 변수 (주입 변수 표의 이름 전체, UI_ALLOW_NON_LOOPBACK, SHELLOPTS). PS4 는 start_service 가 따로 지운다.
CLEAR_NAMES="SERVER_PORT SERVER_ADDRESS SEARCH_SERVICE_URL RESERVATION_SERVICE_URL WEBHOOK_URL PAYMENT_WEBHOOK_SECRET U1_URL U2_URL U3_URL UI_HOST UI_ALLOW_NON_LOOPBACK SHELLOPTS"
SVC_NAMES=()
SVC_PIDS=()
CLEANUP_DONE=0

# 설정 오류를 알리고 종료 코드 2로 끝난다 (서비스를 띄우기 전)
die() {
  printf '오류: %s\n' "$*" >&2
  exit 2
}

# 설정 구역의 기본값을 읽고 환경 변수가 있으면 덮어쓴다. RUN_LOCAL_ROOT 와 RUN_LOCAL_UI_PORT 는 시험용이다.
load_config() {
  ROOT_DIR="${RUN_LOCAL_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
  LOCAL_RUN_DIR="$ROOT_DIR/.local-run"
  U1_PORT="${U1_PORT:-$DEFAULT_U1_PORT}"
  U2_PORT="${U2_PORT:-$DEFAULT_U2_PORT}"
  U3_PORT="${U3_PORT:-$DEFAULT_U3_PORT}"
  UI_PORT="${RUN_LOCAL_UI_PORT:-$DEFAULT_UI_PORT}"
  SERVER_ADDRESS="${SERVER_ADDRESS:-$LOOPBACK}"
  UI_HOST="${UI_HOST:-$LOOPBACK}"
  RUN_LOCAL_READY_TIMEOUT="${RUN_LOCAL_READY_TIMEOUT:-$DEFAULT_READY_TIMEOUT}"
  RUN_LOCAL_POLL_INTERVAL="${RUN_LOCAL_POLL_INTERVAL:-$DEFAULT_POLL_INTERVAL}"
  RUN_LOCAL_WATCH_INTERVAL="${RUN_LOCAL_WATCH_INTERVAL:-$DEFAULT_WATCH_INTERVAL}"
  RUN_LOCAL_TERM_GRACE="${RUN_LOCAL_TERM_GRACE:-$DEFAULT_TERM_GRACE}"
}

validate_port() {
  local name="$1" value="$2"
  case "$value" in
    ''|*[!0-9]*|0*)
      die "$name='$value': 1024~65535 사이의 정수여야 합니다 (숫자만, 앞에 0 없음)"
      ;;
  esac
  if [ "${#value}" -gt 5 ] || [ "$value" -lt 1024 ] || [ "$value" -gt 65535 ]; then die "$name='$value': 1024~65535 범위를 벗어났습니다"; fi
}

validate_distinct() {
  if [ "$2" = "$4" ]; then die "$1 와 $3 이(가) 같은 포트($2)입니다. 서로 달라야 합니다"; fi
}

validate_loopback() {
  case "$2" in
    127.0.0.1|localhost|::1)
      return 0
      ;;
  esac
  die "$1='$2': 루프백 주소(127.0.0.1, localhost, ::1)만 허용합니다. 다른 기기가 접속할 수 있는 주소는 거부합니다"
}

validate_integer() {
  case "$2" in
    ''|*[!0-9]*)
      die "$1='$2': 0 이상의 정수여야 합니다"
      ;;
  esac
}

validate_number() {
  case "$2" in
    ''|*[!0-9.]*|*.*.*|.)
      die "$1='$2': 0 이상의 숫자여야 합니다"
      ;;
  esac
}

# 사용자가 준 비밀 값의 형식과 길이만 검사한다. 값 자체는 메시지에 쓰지 않는다.
validate_secret() {
  local value="${PAYMENT_WEBHOOK_SECRET:-}"
  if [ -z "$value" ]; then return 0; fi
  if [ "${#value}" -lt "$SECRET_MIN_USER" ]; then die "PAYMENT_WEBHOOK_SECRET: ${SECRET_MIN_USER}자 이상이어야 합니다 (현재 ${#value}자). 값 자체는 출력하지 않습니다"; fi
  case "$value" in
    *[[:space:]]*|*[[:cntrl:]]*|*\"*|*\'*)
      die "PAYMENT_WEBHOOK_SECRET: 공백, 제어 문자, 따옴표를 포함할 수 없습니다. 값 자체는 출력하지 않습니다"
      ;;
  esac
}

validate_config() {
  validate_port U1_PORT "$U1_PORT"
  validate_port U2_PORT "$U2_PORT"
  validate_port U3_PORT "$U3_PORT"
  validate_port 화면포트 "$UI_PORT"
  validate_distinct U1_PORT "$U1_PORT" U2_PORT "$U2_PORT"
  validate_distinct U1_PORT "$U1_PORT" U3_PORT "$U3_PORT"
  validate_distinct U2_PORT "$U2_PORT" U3_PORT "$U3_PORT"
  validate_distinct U1_PORT "$U1_PORT" 화면포트 "$UI_PORT"
  validate_distinct U2_PORT "$U2_PORT" 화면포트 "$UI_PORT"
  validate_distinct U3_PORT "$U3_PORT" 화면포트 "$UI_PORT"
  validate_loopback SERVER_ADDRESS "$SERVER_ADDRESS"
  validate_loopback UI_HOST "$UI_HOST"
  if [ -n "${UI_ALLOW_NON_LOOPBACK+x}" ]; then die "UI_ALLOW_NON_LOOPBACK 가 설정되어 있습니다. 이 스크립트는 비루프백 허용 우회를 쓰지 않으므로 값과 관계없이 거부합니다 (환경에서 제거하세요)"; fi
  validate_integer RUN_LOCAL_READY_TIMEOUT "$RUN_LOCAL_READY_TIMEOUT"
  validate_number RUN_LOCAL_POLL_INTERVAL "$RUN_LOCAL_POLL_INTERVAL"
  validate_number RUN_LOCAL_WATCH_INTERVAL "$RUN_LOCAL_WATCH_INTERVAL"
  validate_integer RUN_LOCAL_TERM_GRACE "$RUN_LOCAL_TERM_GRACE"
  validate_secret
}

# 포트에서 서비스 주소를 계산한다. 서비스는 서로 이 주소로 직접 부른다.
derive_urls() {
  U1_BASE="http://$LOOPBACK:$U1_PORT"
  U2_BASE="http://$LOOPBACK:$U2_PORT"
  U3_BASE="http://$LOOPBACK:$U3_PORT"
  WEBHOOK_ENDPOINT="$U3_BASE/api/payments/webhook"
}

# 사용자 값이 있으면 그대로, 없으면 실행마다 무작위 값을 만든다. 값은 변수에만 두고 파일에 저장하지 않는다.
make_secret() {
  local candidate=""
  if [ -n "${PAYMENT_WEBHOOK_SECRET:-}" ]; then
    SECRET="$PAYMENT_WEBHOOK_SECRET"
    SECRET_SOURCE=env
    return 0
  fi
  candidate="$(openssl rand -hex 32 2>/dev/null)" || candidate=""
  if [ "${#candidate}" -lt "$SECRET_MIN_GENERATED" ]; then candidate="$(od -An -N32 -tx1 /dev/urandom 2>/dev/null | tr -d ' \n')" || candidate=""; fi
  if [ "${#candidate}" -lt "$SECRET_MIN_GENERATED" ]; then die "비밀 값을 만들지 못했습니다 (openssl 과 od 모두 ${SECRET_MIN_GENERATED}자 미만). PAYMENT_WEBHOOK_SECRET 을 ${SECRET_MIN_USER}자 이상으로 직접 주세요"; fi
  SECRET="$candidate"
  SECRET_SOURCE=random
}

# 네 포트가 비어 있는지 확인한다. 쓰이는 포트가 있으면 어느 포트인지 알리고 거부한다.
check_ports() {
  local svc port busy=""
  for svc in $SERVICE_ORDER; do
    port="$(service_port "$svc")"
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then busy="$busy $port($(service_dir "$svc"))"; fi
  done
  if [ -n "$busy" ]; then die "이미 사용 중인 포트가 있습니다:$busy. 쓰고 있는 프로세스를 끝내거나 U1_PORT, U2_PORT, U3_PORT 를 바꾸세요 (화면 포트 ${UI_PORT} 는 바꿀 수 없습니다)"; fi
}

# 컴파일 산출물(target/classes)과 u4 의존성(node_modules)이 있는지 확인한다. 없으면 --prepare 를 안내하고 거부한다.
check_prepared() {
  local svc dir missing=""
  for svc in u1 u2 u3; do
    dir="$(service_dir "$svc")"
    if [ ! -d "$ROOT_DIR/services/$dir/target/classes" ]; then missing="$missing $dir(target/classes)"; fi
  done
  if [ ! -d "$ROOT_DIR/services/u4-web-ui/node_modules" ]; then missing="$missing u4-web-ui(node_modules)"; fi
  if [ -n "$missing" ]; then die "준비되지 않은 빌드:$missing. 먼저 'scripts/run-local.sh --prepare' 를 실행하세요"; fi
}

# 로그 폴더를 소유자 전용(umask 077)으로 만들고 .local-run 자체 .gitignore 를 둔다. 루트 .gitignore 는 건드리지 않는다.
setup_logs() {
  umask 077
  mkdir -p "$LOCAL_RUN_DIR/logs"
  printf '*\n' > "$LOCAL_RUN_DIR/.gitignore"
}

service_dir() {
  case "$1" in
    u1) printf 'u1-search-service\n' ;;
    u2) printf 'u2-reservation-service\n' ;;
    u3) printf 'u3-payment-service\n' ;;
    u4) printf 'u4-web-ui\n' ;;
  esac
}

service_port() {
  case "$1" in
    u1) printf '%s\n' "$U1_PORT" ;;
    u2) printf '%s\n' "$U2_PORT" ;;
    u3) printf '%s\n' "$U3_PORT" ;;
    u4) printf '%s\n' "$UI_PORT" ;;
  esac
}

# 서비스를 자기 프로세스 그룹에서 </dev/null 로 띄운다 (set -m 아래의 $! 가 그룹 ID).
# 환경은 먼저 주입 변수 표의 이름 전체를 지우고 그 서비스의 변수만 다시 준다. 비밀 값은 인자가 아닌 서브셸의 export 로 u3 에만 준다.
start_service() {
  local svc="$1" dir logf name
  local -a unset_args=(-u PS4)
  local -a env_set cmd
  dir="$(service_dir "$svc")"
  logf="$LOCAL_RUN_DIR/logs/$dir.log"
  for name in $CLEAR_NAMES; do
    if [ "$svc" = "u3" ] && [ "$name" = "PAYMENT_WEBHOOK_SECRET" ]; then continue; fi
    unset_args+=(-u "$name")
  done
  case "$svc" in
    u1)
      env_set=("SERVER_PORT=$U1_PORT" "SERVER_ADDRESS=$LOOPBACK")
      cmd=(mvn -q -pl "services/$dir" spring-boot:run)
      ;;
    u2)
      env_set=("SERVER_PORT=$U2_PORT" "SERVER_ADDRESS=$LOOPBACK" "SEARCH_SERVICE_URL=$U1_BASE")
      cmd=(mvn -q -pl "services/$dir" spring-boot:run)
      ;;
    u3)
      env_set=("SERVER_PORT=$U3_PORT" "SERVER_ADDRESS=$LOOPBACK" "RESERVATION_SERVICE_URL=$U2_BASE" "WEBHOOK_URL=$WEBHOOK_ENDPOINT")
      cmd=(mvn -q -pl "services/$dir" spring-boot:run)
      ;;
    u4)
      env_set=("U1_URL=$U1_BASE" "U2_URL=$U2_BASE" "U3_URL=$U3_BASE" "UI_HOST=$LOOPBACK")
      cmd=(npm --prefix "services/$dir" run dev)
      ;;
  esac
  ( if [ "$svc" = "u3" ]; then export PAYMENT_WEBHOOK_SECRET="$SECRET"; fi; cd "$ROOT_DIR"; exec env "${unset_args[@]}" "${env_set[@]}" "${cmd[@]}" </dev/null >"$logf" 2>&1 ) &
  SVC_NAMES+=("$svc")
  SVC_PIDS+=("$!")
}

pid_of() {
  local i=0
  while [ "$i" -lt "${#SVC_NAMES[@]}" ]; do
    if [ "${SVC_NAMES[$i]}" = "$1" ]; then
      printf '%s\n' "${SVC_PIDS[$i]}"
      return 0
    fi
    i=$((i + 1))
  done
}

# 신호가 오면 바로 깨어나도록 sleep 을 백그라운드로 두고 wait 한다
pause() {
  sleep "$1" &
  wait "$!" 2>/dev/null || true
}

new_uuid() {
  local u hex
  u="$(uuidgen 2>/dev/null | tr 'A-Z' 'a-z')" || u=""
  if [ "${#u}" -eq 36 ]; then
    printf '%s\n' "$u"
    return 0
  fi
  hex="$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')"
  printf '%s-%s-4%s-a%s-%s\n' "${hex:0:8}" "${hex:8:4}" "${hex:13:3}" "${hex:17:3}" "${hex:20:12}"
}

# u1~u3: /actuator/health 본문에 "status":"UP". u4: / 가 200 이고 프록시 점검(POST /api/search/recommendations, 본문 {})이 정확히 400 (G3)
probe_ui() {
  local code
  code="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 2 "http://$LOOPBACK:$UI_PORT/" 2>/dev/null)" || return 1
  if [ "$code" != "200" ]; then return 1; fi
  if [ -z "${SESSION_ID:-}" ]; then SESSION_ID="$(new_uuid)"; fi
  code="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 2 -X POST -H 'Content-Type: application/json' -H "X-Session-Id: $SESSION_ID" --data '{}' "http://$LOOPBACK:$UI_PORT/api/search/recommendations" 2>/dev/null)" || return 1
  if [ "$code" = "400" ]; then return 0; fi
  return 1
}

probe_ready() {
  local svc="$1" body
  if [ "$svc" = "u4" ]; then
    if probe_ui; then return 0; fi
    return 1
  fi
  body="$(curl --silent --max-time 2 "http://$LOOPBACK:$(service_port "$svc")/actuator/health" 2>/dev/null)" || return 1
  body="${body// /}"
  case "$body" in
    *'"status":"UP"'*)
      return 0
      ;;
  esac
  return 1
}

show_log_tail() {
  local dir
  dir="$(service_dir "$1")"
  printf -- '--- %s 로그 마지막 20줄 ---\n' "$dir" >&2
  tail -n 20 "$LOCAL_RUN_DIR/logs/$dir.log" >&2 || true
  printf -- '--- 로그 끝 ---\n' >&2
}

# 기동 실패를 알리고 그 서비스 로그의 마지막 20줄을 보인 뒤 1로 끝난다 (EXIT trap 이 정리한다)
fail_start() {
  printf '오류: %s 를 띄우지 못했습니다: %s\n' "$(service_dir "$1")" "$2" >&2
  show_log_tail "$1"
  exit 1
}

wait_ready() {
  local svc="$1" pid start rc
  pid="$(pid_of "$svc")"
  start="$SECONDS"
  while :; do
    if ! kill -0 "$pid" 2>/dev/null; then
      rc=0
      wait "$pid" 2>/dev/null || rc=$?
      fail_start "$svc" "프로세스가 준비되기 전에 종료되었습니다 (종료 코드 $rc)"
    fi
    if probe_ready "$svc"; then break; fi
    if [ $((SECONDS - start)) -ge "$RUN_LOCAL_READY_TIMEOUT" ]; then fail_start "$svc" "준비 시간 초과 (${RUN_LOCAL_READY_TIMEOUT}초 안에 준비되지 않았습니다)"; fi
    pause "$RUN_LOCAL_POLL_INTERVAL"
  done
  printf '%s 준비 완료 (%s초)\n' "$(service_dir "$svc")" "$((SECONDS - start))"
}

announce() {
  printf '\n모든 서비스가 준비되었습니다.\n'
  printf '  화면(접속 주소): http://%s:%s/\n' "$LOOPBACK" "$UI_PORT"
  printf '  u1 검색:        %s\n' "$U1_BASE"
  printf '  u2 예약:        %s\n' "$U2_BASE"
  printf '  u3 결제:        %s\n' "$U3_BASE"
  printf '  로그:           %s/logs/\n' "$LOCAL_RUN_DIR"
  printf '정지하려면 Ctrl-C 를 누르세요 (띄운 서비스를 모두 정리합니다).\n'
}

# 기동 뒤 포그라운드에서 대기하며 간격마다 서비스 생존을 본다. 하나라도 끝나면 알리고 1로 끝난다 (EXIT trap 이 나머지를 정리한다).
watch_services() {
  local i
  while :; do
    pause "$RUN_LOCAL_WATCH_INTERVAL"
    i=0
    while [ "$i" -lt "${#SVC_PIDS[@]}" ]; do
      if ! kill -0 "${SVC_PIDS[$i]}" 2>/dev/null; then
        printf '오류: %s 가 종료되었습니다. 나머지 서비스를 모두 정리하고 끝냅니다.\n' "$(service_dir "${SVC_NAMES[$i]}")" >&2
        show_log_tail "${SVC_NAMES[$i]}"
        exit 1
      fi
      i=$((i + 1))
    done
  done
}

# 살아 있는 그룹이 하나라도 있으면 성공(0)
groups_alive() {
  local pid
  for pid in "${SVC_PIDS[@]}"; do
    if kill -0 -- "-$pid" 2>/dev/null; then return 0; fi
  done
  return 1
}

signal_groups() {
  local pid
  for pid in "${SVC_PIDS[@]}"; do
    kill "-$1" -- "-$pid" 2>/dev/null || true
  done
}

ports_still_open() {
  local svc port open=""
  for svc in $SERVICE_ORDER; do
    port="$(service_port "$svc")"
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then open="$open $port"; fi
  done
  printf '%s\n' "$open"
}

# 정리: 그룹마다 SIGTERM -> 유예 -> 남으면 SIGKILL -> 포트 닫힘 확인. 한 번만 실행하고 처음부터 끝까지 INT/TERM/HUP 를 무시한다.
cleanup() {
  local start open
  if [ "$CLEANUP_DONE" = "1" ]; then return 0; fi
  CLEANUP_DONE=1
  trap '' INT TERM HUP
  set +e
  if [ "${#SVC_PIDS[@]}" -eq 0 ]; then return 0; fi
  printf '정리 시작: 띄운 서비스 %s개를 종료합니다 (SIGTERM, 최대 %s초 유예)\n' "${#SVC_PIDS[@]}" "$RUN_LOCAL_TERM_GRACE"
  signal_groups TERM
  start="$SECONDS"
  while groups_alive && [ $((SECONDS - start)) -lt "$RUN_LOCAL_TERM_GRACE" ]; do pause 0.2; done
  if groups_alive; then
    printf '유예 시간 안에 끝나지 않아 SIGKILL 로 강제 종료합니다\n'
    signal_groups KILL
    pause 0.3
  fi
  for pid in "${SVC_PIDS[@]}"; do
    wait "$pid" 2>/dev/null || true
  done
  start="$SECONDS"
  open="$(ports_still_open)"
  while [ -n "$open" ] && [ $((SECONDS - start)) -lt 5 ]; do
    pause 0.2
    open="$(ports_still_open)"
  done
  if [ -n "$open" ]; then printf '경고: 정리 뒤에도 열려 있는 포트가 있습니다:%s\n' "$open" >&2; fi
  printf '정리 완료\n'
  return 0
}

# INT/TERM/HUP 처리기: 이후 신호를 무시하고 관례의 종료 코드로 끝난다 (EXIT trap 이 cleanup 을 부른다)
on_signal() {
  trap '' INT TERM HUP
  printf '\n%s 신호를 받았습니다.\n' "$1"
  case "$1" in
    INT)
      exit 130
      ;;
    TERM)
      exit 143
      ;;
    HUP)
      exit 129
      ;;
  esac
}

prepare() {
  printf '준비: mvn -q -DskipTests compile\n'
  if ! ( cd "$ROOT_DIR" && env -u SHELLOPTS -u PS4 mvn -q -DskipTests compile ); then die "준비 실패: mvn -q -DskipTests compile 이 실패했습니다"; fi
  printf '준비: npm --prefix services/u4-web-ui install\n'
  if ! ( cd "$ROOT_DIR" && env -u SHELLOPTS -u PS4 npm --prefix services/u4-web-ui install ); then die "준비 실패: npm --prefix services/u4-web-ui install 이 실패했습니다"; fi
  printf '준비 완료. 이제 scripts/run-local.sh 로 서비스를 띄울 수 있습니다.\n'
}

usage() {
  printf '%s\n' "사용법: scripts/run-local.sh [--prepare | --help]"
  printf '%s\n' "  (인자 없음)  u1 -> u2 -> u3 -> u4 순서로 한 번에 띄우고, 접속 주소를 알린 뒤 포그라운드에서 대기합니다."
  printf '%s\n' "               Ctrl-C 가 유일한 정지 수단이며 띄운 서비스를 모두 정리합니다. Docker 는 쓰지 않습니다."
  printf '%s\n' "  --prepare    첫 실행 전 컴파일(mvn -q -DskipTests compile)과 npm install 을 따로 실행합니다."
  printf '%s\n' "  --help       이 도움말을 보입니다."
  printf '%s\n' ""
  printf '%s\n' "환경 변수 (기본값):"
  printf '  U1_PORT=%s  U2_PORT=%s  U3_PORT=%s   서비스 포트 (1024~65535, 서로 달라야 함)\n' "$DEFAULT_U1_PORT" "$DEFAULT_U2_PORT" "$DEFAULT_U3_PORT"
  printf '  (화면 포트 %s 는 u4 가 고정하므로 바꿀 수 없습니다)\n' "$DEFAULT_UI_PORT"
  printf '  SERVER_ADDRESS=%s  UI_HOST=%s   루프백 값만 허용 (다른 값이면 거부)\n' "$LOOPBACK" "$LOOPBACK"
  printf '%s\n' "  UI_ALLOW_NON_LOOPBACK   설정되어 있으면 값과 관계없이 거부"
  printf '%s\n' "  PAYMENT_WEBHOOK_SECRET  웹훅 비밀 값 (32자 이상, 공백·제어 문자·따옴표 없음). 없으면 실행마다 무작위 값을 만듭니다. 값은 출력하지 않습니다."
  printf '  RUN_LOCAL_READY_TIMEOUT=%s  서비스당 준비 확인 제한(초)\n' "$DEFAULT_READY_TIMEOUT"
  printf '  RUN_LOCAL_POLL_INTERVAL=%s  준비 확인 폴링 간격(초)\n' "$DEFAULT_POLL_INTERVAL"
  printf '  RUN_LOCAL_WATCH_INTERVAL=%s  기동 뒤 생존 감시 간격(초)\n' "$DEFAULT_WATCH_INTERVAL"
  printf '  RUN_LOCAL_TERM_GRACE=%s  종료 신호 뒤 강제 종료까지의 유예(초)\n' "$DEFAULT_TERM_GRACE"
  printf '%s\n' ""
  printf '%s\n' "로그: .local-run/logs/<서비스>.log (실행마다 덮어쓰기, 소유자만 읽기)"
}

main() {
  local mode=run svc
  if [ "$#" -gt 1 ]; then
    usage >&2
    exit 2
  fi
  case "${1:-}" in
    '')
      mode=run
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --prepare)
      mode=prepare
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
  load_config
  if [ "$mode" = "prepare" ]; then
    prepare
    exit 0
  fi
  validate_config
  derive_urls
  check_prepared
  check_ports
  make_secret
  if [ "$SECRET_SOURCE" = "env" ]; then printf '비밀 값: 환경 변수 값 사용\n'; else printf '비밀 값: 무작위 값 생성\n'; fi
  set -m
  setup_logs
  trap cleanup EXIT
  trap 'on_signal INT' INT
  trap 'on_signal TERM' TERM
  trap 'on_signal HUP' HUP
  for svc in $SERVICE_ORDER; do
    start_service "$svc"
    wait_ready "$svc"
  done
  announce
  watch_services
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi

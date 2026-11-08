#!/bin/bash
# 가짜 mvn/npm/docker. 시험에서 PATH 앞에 둔 얇은 래퍼가 `fake-service.sh <도구> <인자...>` 로 부른다.
# 서비스 실행 명령이면 자기 환경과 인자를 FAKE_STATE_DIR 에 기록하고, 자식·손자 프로세스와 Node 서버를 만든다.
# 동작 모드는 FAKE_MODE_U1..U4 환경 변수로 받는다:
#   normal(기본) | exit(즉시 종료) | no-listen(포트를 열지 않음) | down(상태 확인이 DOWN) | ignore-term(SIGTERM 무시)
STATE="${FAKE_STATE_DIR:?FAKE_STATE_DIR 가 필요합니다}"
FAKES_HERE="$(cd "$(dirname "$0")" && pwd)"
TOOL="$1"
shift

SVC=""
case "$*" in
  *services/u1-search-service*spring-boot:run*)
    SVC=u1
    ;;
  *services/u2-reservation-service*spring-boot:run*)
    SVC=u2
    ;;
  *services/u3-payment-service*spring-boot:run*)
    SVC=u3
    ;;
  *services/u4-web-ui*"run dev"*)
    SVC=u4
    ;;
esac

if [ -z "$SVC" ]; then
  # 서비스 실행이 아닌 호출(compile, install, verify, docker 등)은 기록만 한다.
  printf '%s %s\n' "$TOOL" "$*" >> "$STATE/calls.log"
  case "$TOOL" in
    mvn) exit "${FAKE_RC_MVN:-${FAKE_OTHER_RC:-0}}" ;;
    npm) exit "${FAKE_RC_NPM:-${FAKE_OTHER_RC:-0}}" ;;
    *) exit "${FAKE_OTHER_RC:-0}" ;;
  esac
fi

case "$SVC" in
  u1) MODE="${FAKE_MODE_U1:-normal}" ;;
  u2) MODE="${FAKE_MODE_U2:-normal}" ;;
  u3) MODE="${FAKE_MODE_U3:-normal}" ;;
  u4) MODE="${FAKE_MODE_U4:-normal}" ;;
esac

printf '%s\n' "$SVC" >> "$STATE/order.log"
printf '%s %s\n' "$TOOL" "$*" > "$STATE/$SVC.args"
env | sort > "$STATE/$SVC.env"
printf '%s\n' "$$" > "$STATE/$SVC.pid"
ps -o pgid= -p "$$" | tr -d ' ' > "$STATE/$SVC.pgid"
printf '%s\n' "$$" > "$STATE/$SVC.pids"

if [ "$SVC" = "u4" ]; then
  PORT="${FAKE_U4_PORT:-5173}"
else
  PORT="${SERVER_PORT:-0}"
fi

printf 'fake %s 시작 (모드 %s)\n' "$SVC" "$MODE"

if [ "$MODE" = "exit" ]; then
  n=1
  while [ "$n" -le 30 ]; do
    printf 'fake-%s 로그 줄 %s\n' "$SVC" "$n"
    n=$((n + 1))
  done
  exit 3
fi

if [ "$MODE" = "ignore-term" ]; then
  trap '' TERM
  export FAKE_IGNORE_TERM=1
fi

# 자식 셸 -> 손자 sleep. 둘 다 pids 파일에 남긴다.
/bin/bash -c 'if [ "$2" = "ignore-term" ]; then trap "" TERM; fi; echo $$ >> "$1"; sleep 600 & echo $! >> "$1"; wait' tree "$STATE/$SVC.pids" "$MODE" &

if [ "$MODE" != "no-listen" ]; then
  if [ "$MODE" = "down" ]; then
    export FAKE_HEALTH=DOWN
  fi
  FAKE_ROLE="$SVC" FAKE_PORT="$PORT" node "$FAKES_HERE/fake-server.js" &
  printf '%s\n' "$!" >> "$STATE/$SVC.pids"
fi

while :; do
  wait
  sleep 1
done

#!/usr/bin/env bash
set -euo pipefail

# 실행 중인 전체 서비스에서 AC5.1.2 를 자동으로 확인하는 연동 스모크.
# 화면 프록시(5173)를 통해 추천 -> 결제 -> 예약 CONFIRMED 를 확인하고, 프록시 경유 웹훅·내부 경로가 서비스에 닿지 않음을 함께 확인한다.
# 결과(단계별 성공·실패와 걸린 시간)는 .local-run/smoke.txt 에 남는다. 서비스를 띄우는 일은 하지 않는다 (scripts/run-local.sh 가 한다).

# ===== 설정 구역 =====
DEFAULT_BASE_URL=http://127.0.0.1:5173
DEFAULT_CONFIRM_TIMEOUT=5
DEFAULT_POLL_INTERVAL=0.5
SMOKE_TEXT="강남역 근처 2시간 주차권 찾아줘"
SMOKE_TOTAL=5

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${RUN_LOCAL_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
LOCAL_RUN_DIR="$ROOT_DIR/.local-run"
BASE_URL="${SMOKE_BASE_URL:-$DEFAULT_BASE_URL}"
CONFIRM_TIMEOUT="${SMOKE_CONFIRM_TIMEOUT:-$DEFAULT_CONFIRM_TIMEOUT}"
POLL_INTERVAL="${SMOKE_POLL_INTERVAL:-$DEFAULT_POLL_INTERVAL}"
LOG_DIR="${SMOKE_LOG_DIR:-$LOCAL_RUN_DIR/logs}"
RESULT_FILE="$LOCAL_RUN_DIR/smoke.txt"
BODY_FILE="$LOCAL_RUN_DIR/smoke-body.tmp"
PASSED=0
FAILED=0
STATUS=000
BODY=""

usage() {
  printf '%s\n' "사용법: scripts/smoke-local.sh   (서비스가 이미 떠 있어야 합니다: scripts/run-local.sh)"
  printf '%s\n' "환경 변수: SMOKE_BASE_URL(기본 $DEFAULT_BASE_URL), SMOKE_CONFIRM_TIMEOUT(기본 ${DEFAULT_CONFIRM_TIMEOUT}초)"
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

# 단계 결과를 화면과 smoke.txt 에 남긴다: record <PASS|FAIL|SKIP> <이름> <시작 SECONDS> <설명>
record() {
  local line
  line="$(printf '[%s] %s (%s초): %s' "$1" "$2" "$((SECONDS - $3))" "$4")"
  printf '%s\n' "$line"
  printf '%s\n' "$line" >> "$RESULT_FILE"
  if [ "$1" = "PASS" ]; then PASSED=$((PASSED + 1)); fi
  if [ "$1" = "FAIL" ]; then FAILED=$((FAILED + 1)); fi
}

# http_request <메서드> <경로> [JSON 본문] -> STATUS, BODY (연결 실패는 000)
http_request() {
  local method="$1" path="$2" data="${3:-}"
  if [ -n "$data" ]; then
    STATUS="$(curl --silent --output "$BODY_FILE" --write-out '%{http_code}' --max-time 10 -X "$method" -H 'Content-Type: application/json' -H "X-Session-Id: $SESSION_ID" --data "$data" "$BASE_URL$path" 2>/dev/null)" || STATUS=000
  else
    STATUS="$(curl --silent --output "$BODY_FILE" --write-out '%{http_code}' --max-time 10 -X "$method" -H "X-Session-Id: $SESSION_ID" "$BASE_URL$path" 2>/dev/null)" || STATUS=000
  fi
  BODY="$(cat "$BODY_FILE" 2>/dev/null || true)"
}

count_log() {
  local n
  n="$(grep -c -F -- "$2" "$1" 2>/dev/null)" || n=0
  printf '%s\n' "${n:-0}"
}

log_supported() {
  grep -q -E '/api/|/internal/' "$1" 2>/dev/null
}

# 프록시 경유 요청이 서비스에 닿지 않는지 본다. 판정은 상태 코드(404 또는 405)가 하고 로그 줄 수 비교는 보조다 (G4).
check_guard() {
  local name="$1" path="$2" logfile="$3" pattern="$4" start before after note
  start="$SECONDS"
  before="$(count_log "$logfile" "$pattern")"
  http_request POST "$path" '{}'
  after="$(count_log "$logfile" "$pattern")"
  if [ "$STATUS" != "404" ] && [ "$STATUS" != "405" ]; then
    record FAIL "$name" "$start" "상태 $STATUS (404 또는 405 여야 서비스에 닿지 않은 것입니다)"
    return 0
  fi
  if [ "$after" -gt "$before" ]; then
    record FAIL "$name" "$start" "상태 $STATUS 이지만 서비스 로그에 요청 줄이 $((after - before))건 늘었습니다 (서비스에 닿음)"
    return 0
  fi
  if log_supported "$logfile"; then note="로그 줄 수 동일"; else note="참고(로그 미지원: 서비스가 요청 줄을 남기지 않아 로그 비교는 판정에 쓰지 않음)"; fi
  record PASS "$name" "$start" "상태 $STATUS, $note"
}

json_field() {
  printf '%s' "$BODY" | grep -o "\"$1\" *: *\"\\{0,1\\}[^\",}]*" | head -n 1 | sed 's/.*: *"\{0,1\}//' || true
}

main() {
  local start pass_id duration payload key deadline
  if [ "$#" -gt 0 ]; then
    usage >&2
    exit 2
  fi
  umask 077
  mkdir -p "$LOCAL_RUN_DIR"
  if [ ! -f "$LOCAL_RUN_DIR/.gitignore" ]; then printf '*\n' > "$LOCAL_RUN_DIR/.gitignore"; fi
  SESSION_ID="$(new_uuid)"
  printf '스모크 시작: %s (%s)\n' "$BASE_URL" "$(date '+%Y-%m-%d %H:%M:%S')" | tee "$RESULT_FILE"

  check_guard "웹훅 경로 미도달" /api/payments/webhook "$LOG_DIR/u3-payment-service.log" /api/payments/webhook
  check_guard "내부 경로 미도달" /internal/reservations "$LOG_DIR/u2-reservation-service.log" /internal/reservations

  start="$SECONDS"
  payload="$(printf '{"text":"%s"}' "$SMOKE_TEXT")"
  http_request POST /api/search/recommendations "$payload"
  pass_id="$(json_field passId)"
  duration="$(json_field durationHours)"
  if [ "$STATUS" = "200" ] && [ -n "$pass_id" ] && [ -n "$duration" ]; then
    record PASS "추천 요청" "$start" "상태 200, 최적 후보 $pass_id (이용 ${duration}시간)"
  else
    record FAIL "추천 요청" "$start" "상태 $STATUS, 후보를 받지 못했습니다"
    record SKIP "결제 요청" "$SECONDS" "추천 실패로 건너뜀"
    record SKIP "예약 CONFIRMED 확인" "$SECONDS" "추천 실패로 건너뜀"
    finish
    return 0
  fi

  start="$SECONDS"
  key="$(new_uuid)"
  payload="$(printf '{"passId":"%s","durationHours":%s,"idempotencyKey":"%s","mockScenario":"APPROVE"}' "$pass_id" "$duration" "$key")"
  http_request POST /api/payments "$payload"
  if [ "$STATUS" = "202" ]; then
    record PASS "결제 요청" "$start" "상태 202 (승인 시나리오)"
  else
    record FAIL "결제 요청" "$start" "상태 $STATUS (202 여야 합니다)"
    record SKIP "예약 CONFIRMED 확인" "$SECONDS" "결제 요청 실패로 건너뜀"
    finish
    return 0
  fi

  start="$SECONDS"
  deadline=$((SECONDS + CONFIRM_TIMEOUT))
  while :; do
    http_request GET /api/reservations/current
    BODY="${BODY// /}"
    case "$BODY" in
      *'"status":"CONFIRMED"'*)
        record PASS "예약 CONFIRMED 확인" "$start" "예약 완료 상태 확인"
        break
        ;;
    esac
    if [ "$SECONDS" -ge "$deadline" ]; then
      record FAIL "예약 CONFIRMED 확인" "$start" "${CONFIRM_TIMEOUT}초 안에 CONFIRMED 가 되지 않았습니다 (마지막 상태 코드 $STATUS)"
      break
    fi
    sleep "$POLL_INTERVAL"
  done
  finish
}

finish() {
  printf '결과: 통과 %s/%s\n' "$PASSED" "$SMOKE_TOTAL" | tee -a "$RESULT_FILE"
  printf '기록: %s\n' "$RESULT_FILE"
  if [ "$FAILED" -gt 0 ] || [ "$PASSED" -lt "$SMOKE_TOTAL" ]; then exit 1; fi
}

main "$@"

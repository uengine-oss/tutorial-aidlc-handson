#!/bin/bash
# smoke-local.sh 자체 시험. 가짜 서버(fake-server.js)가 화면 프록시(5173) 역할을 한다. /bin/bash 3.2 호환.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

suite_begin smoke-local

SMOKE="$SCRIPTS_DIR/smoke-local.sh"

# 가짜 서버를 띄우고 스모크가 읽을 환경을 만든다. 서버 로그 파일은 $WORK/logs 아래 (u2, u3 로그 역할)
start_fake_ui() {
  mkdir -p "$WORK/ctl" "$WORK/logs"
  : > "$WORK/logs/u2-reservation-service.log"
  : > "$WORK/logs/u3-payment-service.log"
  export FAKE_CTL_DIR="$WORK/ctl"
  export FAKE_LOG_U2="$WORK/logs/u2-reservation-service.log"
  export FAKE_LOG_U3="$WORK/logs/u3-payment-service.log"
  FAKE_ROLE=u4 FAKE_PORT="$P4" node "$FAKES_DIR/fake-server.js" &
  echo $! >> "$WORK/state/listener.pid"
  wait_until 30 port_open "$P4"
  export SMOKE_BASE_URL="http://127.0.0.1:$P4"
  export SMOKE_LOG_DIR="$WORK/logs"
  export SMOKE_POLL_INTERVAL=0.3
  export SMOKE_CONFIRM_TIMEOUT="${SMOKE_CONFIRM_TIMEOUT:-20}"
}

run_smoke() {
  SMOKE_RC=0
  /bin/bash "$SMOKE" > "$WORK/smoke-out.txt" 2> "$WORK/smoke-err.txt" || SMOKE_RC=$?
  SMOKE_TXT="$WORK/root/.local-run/smoke.txt"
}

smoke_line() {
  grep -F -- "$1" "$SMOKE_TXT" 2>/dev/null | head -n 1
}

case_smoke_passes_end_to_end() {
  new_scenario smoke-pass
  start_fake_ui
  run_smoke
  assert_eq "$SMOKE_RC" "0" "모든 단계가 통과하면 0"
  assert_true "smoke.txt 가 만들어짐" test -s "$SMOKE_TXT"
  assert_contains "$(smoke_line '추천')" "[PASS]" "추천 단계 통과"
  assert_contains "$(smoke_line '결제 요청')" "[PASS]" "결제 요청 단계 통과"
  assert_contains "$(smoke_line 'CONFIRMED')" "[PASS]" "예약 CONFIRMED 단계 통과"
  assert_contains "$(smoke_line '웹훅')" "[PASS]" "웹훅 경로 미도달 통과"
  assert_contains "$(smoke_line '내부 경로')" "[PASS]" "내부 경로 미도달 통과"
  assert_contains "$(cat "$SMOKE_TXT")" "초)" "단계별 걸린 시간을 남김"
  assert_contains "$(cat "$SMOKE_TXT")" "통과 5/5" "요약"
  assert_file_has "$WORK/ctl/requests.log" "POST /api/payments sid=" "결제 요청이 세션 헤더와 함께 전달됨"
  assert_true "추천 요청에 UUID 세션 헤더" grep -Eq 'POST /api/search/recommendations sid=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12} ct=application/json' "$WORK/ctl/requests.log"
}

case_smoke_polls_until_confirmed() {
  new_scenario smoke-poll
  export FAKE_CONFIRM_DELAY_MS=1500
  start_fake_ui
  run_smoke
  assert_eq "$SMOKE_RC" "0" "지연 뒤 CONFIRMED 가 되어도 폴링으로 통과"
  assert_contains "$(smoke_line 'CONFIRMED')" "[PASS]" "CONFIRMED 단계 통과"
}

case_smoke_fails_when_never_confirmed() {
  new_scenario smoke-noconfirm
  export FAKE_RESERVATION_FINAL=PENDING_PAYMENT SMOKE_CONFIRM_TIMEOUT=2
  start_fake_ui
  run_smoke
  assert_true "CONFIRMED 가 되지 않으면 0이 아닌 종료 코드" test "$SMOKE_RC" -ne 0
  assert_contains "$(smoke_line 'CONFIRMED')" "[FAIL]" "CONFIRMED 단계 실패 기록"
}

case_smoke_fails_when_recommendation_fails() {
  new_scenario smoke-norec
  export FAKE_SEARCH_STATUS=500
  start_fake_ui
  run_smoke
  assert_true "추천 실패는 0이 아닌 종료 코드" test "$SMOKE_RC" -ne 0
  assert_contains "$(smoke_line '추천')" "[FAIL]" "추천 단계 실패 기록"
  assert_contains "$(smoke_line '결제 요청')" "[SKIP]" "뒤 단계는 건너뜀"
}

case_smoke_fails_when_payment_rejected() {
  new_scenario smoke-nopay
  export FAKE_PAYMENT_STATUS=502
  start_fake_ui
  run_smoke
  assert_true "결제 실패는 0이 아닌 종료 코드" test "$SMOKE_RC" -ne 0
  assert_contains "$(smoke_line '결제 요청')" "[FAIL]" "결제 요청 단계 실패 기록"
  assert_contains "$(smoke_line 'CONFIRMED')" "[SKIP]" "예약 확인은 건너뜀"
}

case_smoke_fails_when_server_down() {
  new_scenario smoke-down
  mkdir -p "$WORK/logs"
  export SMOKE_BASE_URL="http://127.0.0.1:$P4" SMOKE_LOG_DIR="$WORK/logs" SMOKE_CONFIRM_TIMEOUT=2
  run_smoke
  assert_true "서버가 없으면 0이 아닌 종료 코드" test "$SMOKE_RC" -ne 0
  assert_contains "$(smoke_line '추천')" "[FAIL]" "추천 단계 실패 기록"
}

# G4: 미도달 판정은 상태 코드가 주고 로그 비교는 보조다 (네 경우)
case_guard_404_with_same_logs_passes() {
  new_scenario guard-404
  start_fake_ui
  run_smoke
  assert_contains "$(smoke_line '웹훅')" "[PASS]" "404 이고 로그가 같으면 웹훅 미도달 통과"
  assert_contains "$(smoke_line '내부 경로')" "[PASS]" "404 이고 로그가 같으면 내부 경로 미도달 통과"
}

case_guard_405_passes() {
  new_scenario guard-405
  export FAKE_WEBHOOK_STATUS=405 FAKE_INTERNAL_STATUS=405
  start_fake_ui
  run_smoke
  assert_eq "$SMOKE_RC" "0" "405 도 미도달로 통과"
  assert_contains "$(smoke_line '웹훅')" "405" "상태 405 를 기록"
}

case_guard_200_fails_even_if_logs_same() {
  new_scenario guard-200
  export FAKE_WEBHOOK_STATUS=200 FAKE_INTERNAL_STATUS=200
  start_fake_ui
  run_smoke
  assert_true "200 이면 로그가 같아도 0이 아닌 종료 코드" test "$SMOKE_RC" -ne 0
  assert_contains "$(smoke_line '웹훅')" "[FAIL]" "웹훅이 200 이면 실패"
  assert_contains "$(smoke_line '내부 경로')" "[FAIL]" "내부 경로가 200 이면 실패"
}

case_guard_404_but_log_grows_fails() {
  new_scenario guard-logs
  export FAKE_LOG_GUARDED=1
  start_fake_ui
  run_smoke
  assert_true "상태 404 여도 서비스 로그 줄이 늘면 0이 아닌 종료 코드" test "$SMOKE_RC" -ne 0
  assert_contains "$(smoke_line '웹훅')" "[FAIL]" "u3 로그에 웹훅 요청이 늘면 실패"
  assert_contains "$(smoke_line '내부 경로')" "[FAIL]" "u2 로그에 내부 경로 요청이 늘면 실패"
}

case_guard_without_log_support_is_noted() {
  new_scenario guard-nolog
  start_fake_ui
  rm -rf "$WORK/logs"
  run_smoke
  assert_contains "$(smoke_line '웹훅')" "로그 미지원" "로그가 없으면 참고(로그 미지원)로 표시"
  assert_contains "$(smoke_line '웹훅')" "[PASS]" "로그가 없어도 상태 코드로 판정"
}

run_case case_smoke_passes_end_to_end
run_case case_smoke_polls_until_confirmed
run_case case_smoke_fails_when_never_confirmed
run_case case_smoke_fails_when_recommendation_fails
run_case case_smoke_fails_when_payment_rejected
run_case case_smoke_fails_when_server_down
run_case case_guard_404_with_same_logs_passes
run_case case_guard_405_passes
run_case case_guard_200_fails_even_if_logs_same
run_case case_guard_404_but_log_grows_fails
run_case case_guard_without_log_support_is_noted

suite_end

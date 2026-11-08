#!/bin/bash
# run-local.sh 시나리오 시험. /bin/bash 3.2 로 실행한다. 추가 설치 없음.
# COVERAGE=1 이면 run-local.sh 를 SHELLOPTS=xtrace 로 돌려 줄 실행 기록을 남기고 비밀 값 비출력 확인을 건너뛴다 (G2).
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

suite_begin run-local

FAKE_SECRET_32="test-only-fixed-fake-secret-0123456789abcdef"

# ---------- 설정, 검증, 비밀 값 (Step 3) ----------

# 환경을 설정한 뒤 설정 읽기와 검증을 거치게 하는 공통 호출. 거부면 RL_RC 가 0이 아니다.
validate_now() {
  rl_eval 'load_config; validate_config; derive_urls'
}

expect_rejected() {
  validate_now
  if [ "$RL_RC" != "0" ]; then ok "$1 거부"; else bad "$1 거부" "종료 코드 0으로 통과함"; fi
  assert_contains "$(rl_text)" "$2" "$1: 틀린 값의 이름 $2 을 알림"
}

case_cfg_defaults() {
  new_light cfg-defaults
  rl_eval 'load_config; validate_config; derive_urls; printf "%s\n" "$U1_PORT $U2_PORT $U3_PORT $UI_PORT $SERVER_ADDRESS $UI_HOST $RUN_LOCAL_READY_TIMEOUT $RUN_LOCAL_POLL_INTERVAL $RUN_LOCAL_WATCH_INTERVAL $RUN_LOCAL_TERM_GRACE $U1_BASE $U2_BASE $U3_BASE $WEBHOOK_ENDPOINT"'
  assert_eq "$RL_RC" "0" "기본 설정이 통과"
  assert_eq "$(cat "$RL_OUT")" "8081 8082 8083 5173 127.0.0.1 127.0.0.1 90 1 2 10 http://127.0.0.1:8081 http://127.0.0.1:8082 http://127.0.0.1:8083 http://127.0.0.1:8083/api/payments/webhook" "기본 포트, 호스트, 제한 시간, 주소"
}

case_cfg_port_change_derives_urls() {
  new_light cfg-ports
  export U1_PORT=18081 U2_PORT=18082 U3_PORT=18083
  rl_eval 'load_config; validate_config; derive_urls; printf "%s\n" "$U1_BASE $U2_BASE $U3_BASE $WEBHOOK_ENDPOINT"'
  assert_eq "$(cat "$RL_OUT")" "http://127.0.0.1:18081 http://127.0.0.1:18082 http://127.0.0.1:18083 http://127.0.0.1:18083/api/payments/webhook" "포트 변경이 서비스 주소에 반영"
}

case_cfg_u3_port_changes_webhook_url() {
  new_light cfg-webhook
  export U3_PORT=19093
  rl_eval 'load_config; validate_config; derive_urls; printf "%s\n" "$WEBHOOK_ENDPOINT"'
  assert_eq "$(cat "$RL_OUT")" "http://127.0.0.1:19093/api/payments/webhook" "U3 포트 변경이 웹훅 주소에 반영"
}

case_cfg_invalid_ports() {
  new_light cfg-badports
  local v
  for v in abc 80 1023 65536 99999999999 08081 "8 081" "8081;ls" -1 "\$(id)"; do
    export U1_PORT="$v"
    expect_rejected "U1_PORT=$v" "U1_PORT"
  done
  unset U1_PORT
  export U3_PORT=abc
  expect_rejected "U3_PORT=abc" "U3_PORT"
  unset U3_PORT
  export U2_PORT=1024
  validate_now
  assert_eq "$RL_RC" "0" "경계값 1024 는 통과"
  export U2_PORT=65535
  validate_now
  assert_eq "$RL_RC" "0" "경계값 65535 는 통과"
}

case_cfg_duplicate_ports() {
  new_light cfg-dups
  export U1_PORT=18100 U2_PORT=18100
  expect_rejected "U1_PORT 와 U2_PORT 같음" "U2_PORT"
  assert_contains "$(rl_text)" "U1_PORT" "같은 포트 둘: 두 이름을 모두 알림"
  unset U1_PORT U2_PORT
  export U2_PORT=5173
  expect_rejected "U2_PORT 가 화면 포트 5173 과 같음" "5173"
}

case_cfg_rejects_non_loopback() {
  new_light cfg-loopback
  export SERVER_ADDRESS=0.0.0.0
  expect_rejected "SERVER_ADDRESS=0.0.0.0" "SERVER_ADDRESS"
  unset SERVER_ADDRESS
  export UI_HOST=192.168.0.7
  expect_rejected "UI_HOST=192.168.0.7" "UI_HOST"
  export UI_HOST=0.0.0.0
  expect_rejected "UI_HOST=0.0.0.0" "UI_HOST"
  export UI_HOST=localhost SERVER_ADDRESS=127.0.0.1
  validate_now
  assert_eq "$RL_RC" "0" "루프백 값(localhost, 127.0.0.1)은 통과"
}

case_cfg_rejects_allow_non_loopback() {
  new_light cfg-allow
  export UI_ALLOW_NON_LOOPBACK=1
  expect_rejected "UI_ALLOW_NON_LOOPBACK=1" "UI_ALLOW_NON_LOOPBACK"
  export UI_ALLOW_NON_LOOPBACK=0
  expect_rejected "UI_ALLOW_NON_LOOPBACK=0 (값과 무관)" "UI_ALLOW_NON_LOOPBACK"
  export UI_ALLOW_NON_LOOPBACK=
  expect_rejected "UI_ALLOW_NON_LOOPBACK 빈 값으로 설정" "UI_ALLOW_NON_LOOPBACK"
}

case_cfg_rejects_bad_timeouts() {
  new_light cfg-timeouts
  export RUN_LOCAL_READY_TIMEOUT=abc
  expect_rejected "RUN_LOCAL_READY_TIMEOUT=abc" "RUN_LOCAL_READY_TIMEOUT"
  unset RUN_LOCAL_READY_TIMEOUT
  export RUN_LOCAL_TERM_GRACE="1;ls"
  expect_rejected "RUN_LOCAL_TERM_GRACE 셸 문자" "RUN_LOCAL_TERM_GRACE"
  unset RUN_LOCAL_TERM_GRACE
  export RUN_LOCAL_POLL_INTERVAL=abc
  expect_rejected "RUN_LOCAL_POLL_INTERVAL=abc" "RUN_LOCAL_POLL_INTERVAL"
  export RUN_LOCAL_POLL_INTERVAL=1.2.3
  expect_rejected "RUN_LOCAL_POLL_INTERVAL=1.2.3" "RUN_LOCAL_POLL_INTERVAL"
  unset RUN_LOCAL_POLL_INTERVAL
  export RUN_LOCAL_WATCH_INTERVAL=-1
  expect_rejected "RUN_LOCAL_WATCH_INTERVAL=-1" "RUN_LOCAL_WATCH_INTERVAL"
  unset RUN_LOCAL_WATCH_INTERVAL
  export RUN_LOCAL_POLL_INTERVAL=0.5 RUN_LOCAL_WATCH_INTERVAL=2.5
  validate_now
  assert_eq "$RL_RC" "0" "소수 간격(0.5, 2.5)은 통과"
}

make_secret_now() {
  rl_eval 'load_config; validate_config; derive_urls; make_secret; printf "%s %s\n" "${#SECRET}" "$SECRET_SOURCE"'
}

case_secret_generated_long_enough() {
  new_light secret-gen
  make_secret_now
  assert_eq "$RL_RC" "0" "비밀 값 생성 성공"
  local len source
  len="$(cut -d' ' -f1 "$RL_OUT")"
  source="$(cut -d' ' -f2 "$RL_OUT")"
  assert_true "생성 값은 64자 이상 (길이 $len)" test "$len" -ge 64
  assert_eq "$source" "random" "출처는 무작위 생성"
}

case_secret_user_value_used() {
  new_light secret-user
  export PAYMENT_WEBHOOK_SECRET="$FAKE_SECRET_32"
  make_secret_now
  assert_eq "$RL_RC" "0" "32자 이상 사용자 값 통과"
  assert_eq "$(cat "$RL_OUT")" "${#FAKE_SECRET_32} env" "사용자 값을 그대로 쓰고 출처는 환경 변수"
}

case_secret_user_value_rejected() {
  new_light secret-reject
  export PAYMENT_WEBHOOK_SECRET
  PAYMENT_WEBHOOK_SECRET="$(printf '%s' "$FAKE_SECRET_32" | head -c 31)"
  local short="${#PAYMENT_WEBHOOK_SECRET}"
  expect_rejected "사용자 값 31자" "PAYMENT_WEBHOOK_SECRET"
  if secret_checks_enabled; then
    assert_not_contains "$(rl_text)" "$PAYMENT_WEBHOOK_SECRET" "거부 메시지에 값을 쓰지 않음"
  fi
  export PAYMENT_WEBHOOK_SECRET="has space in the value 0123456789abcdef0123456789"
  expect_rejected "공백이 든 비밀 값" "PAYMENT_WEBHOOK_SECRET"
  export PAYMENT_WEBHOOK_SECRET="has\"quote-0123456789abcdef0123456789abcdef"
  expect_rejected "큰따옴표가 든 비밀 값" "PAYMENT_WEBHOOK_SECRET"
  export PAYMENT_WEBHOOK_SECRET="has'quote-0123456789abcdef0123456789abcdef"
  expect_rejected "작은따옴표가 든 비밀 값" "PAYMENT_WEBHOOK_SECRET"
  export PAYMENT_WEBHOOK_SECRET="$(printf 'ctrl\001char-0123456789abcdef0123456789abcdef')"
  expect_rejected "제어 문자가 든 비밀 값" "PAYMENT_WEBHOOK_SECRET"
  export PAYMENT_WEBHOOK_SECRET="$(printf 'new\nline-0123456789abcdef0123456789abcdef')"
  expect_rejected "개행이 든 비밀 값" "PAYMENT_WEBHOOK_SECRET"
  : "$short"
}

case_secret_openssl_failure_uses_od() {
  new_light secret-od
  printf '#!/bin/bash\nexit 1\n' > "$WORK/fake-bin/openssl"
  chmod +x "$WORK/fake-bin/openssl"
  export PATH="$WORK/fake-bin:$PATH"
  make_secret_now
  assert_eq "$RL_RC" "0" "openssl 실패 시 od 로 대체해 성공"
  local len
  len="$(cut -d' ' -f1 "$RL_OUT")"
  assert_true "대체 생성 값도 64자 이상 (길이 $len)" test "$len" -ge 64
}

case_secret_both_short_rejected() {
  new_light secret-short
  printf '#!/bin/bash\nprintf "abcd"\n' > "$WORK/fake-bin/openssl"
  printf '#!/bin/bash\nprintf " ab cd"\n' > "$WORK/fake-bin/od"
  chmod +x "$WORK/fake-bin/openssl" "$WORK/fake-bin/od"
  export PATH="$WORK/fake-bin:$PATH"
  make_secret_now
  if [ "$RL_RC" != "0" ]; then ok "두 방법 모두 짧으면 거부"; else bad "두 방법 모두 짧으면 거부" "통과함"; fi
  assert_contains "$(rl_text)" "비밀 값" "거부 사유를 알림"
}

# ---------- 서비스 기동과 환경 격리 (Step 6) ----------

# 설정을 읽고 네 서비스를 start_service 로 띄운 뒤 가짜 서비스가 환경을 기록할 때까지 기다린다. 정리는 시험 도우미가 한다.
START_ALL='set -m; load_config; validate_config; derive_urls; make_secret; setup_logs; start_service u1; start_service u2; start_service u3; start_service u4; n=0; while [ "$n" -lt 300 ]; do if [ -s "$FAKE_STATE_DIR/u1.env" ] && [ -s "$FAKE_STATE_DIR/u2.env" ] && [ -s "$FAKE_STATE_DIR/u3.env" ] && [ -s "$FAKE_STATE_DIR/u4.env" ]; then break; fi; n=$((n + 1)); sleep 0.2; done; printf "%s\n" "${SVC_PIDS[0]} ${SVC_PIDS[1]} ${SVC_PIDS[2]} ${SVC_PIDS[3]}"'

# 사용자 셸이 같은 이름의 변수를 이미 내보낸 상태를 만든다 (상속 차단 확인용)
pollute_user_env() {
  export SERVER_PORT=1 SERVER_ADDRESS=localhost UI_HOST=localhost
  export SEARCH_SERVICE_URL=http://evil.invalid:1 RESERVATION_SERVICE_URL=http://evil.invalid:2
  export WEBHOOK_URL=http://evil.invalid:3 U1_URL=http://evil.invalid:4 U2_URL=http://evil.invalid:5 U3_URL=http://evil.invalid:6
}

start_all_fakes() {
  rl_eval "$START_ALL"
}

case_env_matches_injection_table() {
  new_scenario env-table
  start_all_fakes
  assert_eq "$RL_RC" "0" "네 서비스 기동 호출이 성공"
  assert_eq "$(svc_env_value u1 SERVER_PORT)" "$P1" "u1 SERVER_PORT"
  assert_eq "$(svc_env_value u1 SERVER_ADDRESS)" "127.0.0.1" "u1 SERVER_ADDRESS"
  assert_eq "$(svc_env_value u2 SERVER_PORT)" "$P2" "u2 SERVER_PORT"
  assert_eq "$(svc_env_value u2 SEARCH_SERVICE_URL)" "http://127.0.0.1:$P1" "u2 SEARCH_SERVICE_URL 이 u1 주소"
  assert_eq "$(svc_env_value u3 SERVER_PORT)" "$P3" "u3 SERVER_PORT"
  assert_eq "$(svc_env_value u3 RESERVATION_SERVICE_URL)" "http://127.0.0.1:$P2" "u3 RESERVATION_SERVICE_URL 이 u2 주소"
  assert_eq "$(svc_env_value u3 WEBHOOK_URL)" "http://127.0.0.1:$P3/api/payments/webhook" "u3 WEBHOOK_URL 이 자기 웹훅 주소"
  assert_eq "$(svc_env_value u4 U1_URL)" "http://127.0.0.1:$P1" "u4 U1_URL"
  assert_eq "$(svc_env_value u4 U2_URL)" "http://127.0.0.1:$P2" "u4 U2_URL"
  assert_eq "$(svc_env_value u4 U3_URL)" "http://127.0.0.1:$P3" "u4 U3_URL"
  assert_eq "$(svc_env_value u4 UI_HOST)" "127.0.0.1" "u4 UI_HOST"
  assert_false "u4 는 SERVER_PORT 를 받지 않음" svc_env_has u4 SERVER_PORT
  assert_false "u1 은 SEARCH_SERVICE_URL 을 받지 않음" svc_env_has u1 SEARCH_SERVICE_URL
  assert_false "u2 는 WEBHOOK_URL 을 받지 않음" svc_env_has u2 WEBHOOK_URL
  assert_contains "$(cat "$WORK/state/u1.args")" "-q -pl services/u1-search-service spring-boot:run" "u1 은 공개 실행 명령으로 실행"
  assert_contains "$(cat "$WORK/state/u4.args")" "--prefix services/u4-web-ui run dev" "u4 는 공개 실행 명령으로 실행"
}

case_secret_only_reaches_u3() {
  new_scenario env-secret
  export PAYMENT_WEBHOOK_SECRET="$FAKE_SECRET_32"
  start_all_fakes
  assert_eq "$(svc_env_value u3 PAYMENT_WEBHOOK_SECRET)" "$FAKE_SECRET_32" "u3 만 사용자 비밀 값을 받음"
  assert_false "u1 은 PAYMENT_WEBHOOK_SECRET 을 상속하지 않음" svc_env_has u1 PAYMENT_WEBHOOK_SECRET
  assert_false "u2 는 PAYMENT_WEBHOOK_SECRET 을 상속하지 않음" svc_env_has u2 PAYMENT_WEBHOOK_SECRET
  assert_false "u4 는 PAYMENT_WEBHOOK_SECRET 을 상속하지 않음" svc_env_has u4 PAYMENT_WEBHOOK_SECRET
}

case_user_shell_variables_not_inherited() {
  new_scenario env-inherit
  pollute_user_env
  start_all_fakes
  assert_eq "$(svc_env_value u1 SERVER_PORT)" "$P1" "사용자의 SERVER_PORT=1 을 덮어씀"
  assert_false "u1 은 사용자의 SEARCH_SERVICE_URL 을 상속하지 않음" svc_env_has u1 SEARCH_SERVICE_URL
  assert_false "u1 은 사용자의 U1_URL 을 상속하지 않음" svc_env_has u1 U1_URL
  assert_false "u1 은 사용자의 UI_HOST 를 상속하지 않음" svc_env_has u1 UI_HOST
  assert_eq "$(svc_env_value u2 SEARCH_SERVICE_URL)" "http://127.0.0.1:$P1" "u2 는 사용자 값이 아닌 계산한 주소를 받음"
  assert_false "u2 는 사용자의 RESERVATION_SERVICE_URL 을 상속하지 않음" svc_env_has u2 RESERVATION_SERVICE_URL
  assert_eq "$(svc_env_value u3 WEBHOOK_URL)" "http://127.0.0.1:$P3/api/payments/webhook" "u3 WEBHOOK_URL 은 계산한 값"
  assert_false "u3 는 사용자의 SEARCH_SERVICE_URL 을 상속하지 않음" svc_env_has u3 SEARCH_SERVICE_URL
  assert_eq "$(svc_env_value u4 U1_URL)" "http://127.0.0.1:$P1" "u4 U1_URL 은 계산한 값"
  assert_false "u4 는 사용자의 SERVER_PORT 를 상속하지 않음" svc_env_has u4 SERVER_PORT
  assert_false "u4 는 사용자의 WEBHOOK_URL 을 상속하지 않음" svc_env_has u4 WEBHOOK_URL
}

case_all_services_get_loopback() {
  new_scenario env-loopback
  pollute_user_env
  start_all_fakes
  assert_eq "$(svc_env_value u1 SERVER_ADDRESS)" "127.0.0.1" "u1 SERVER_ADDRESS 는 127.0.0.1 (사용자 localhost 를 덮어씀)"
  assert_eq "$(svc_env_value u2 SERVER_ADDRESS)" "127.0.0.1" "u2 SERVER_ADDRESS"
  assert_eq "$(svc_env_value u3 SERVER_ADDRESS)" "127.0.0.1" "u3 SERVER_ADDRESS"
  assert_eq "$(svc_env_value u4 UI_HOST)" "127.0.0.1" "u4 UI_HOST"
  assert_false "UI_ALLOW_NON_LOOPBACK 는 어떤 서비스에도 없음" svc_env_has u4 UI_ALLOW_NON_LOOPBACK
}

case_shellopts_not_inherited() {
  new_scenario env-shellopts
  start_all_fakes
  assert_false "u1 은 SHELLOPTS 를 받지 않음" svc_env_has u1 SHELLOPTS
  assert_false "u3 은 SHELLOPTS 를 받지 않음" svc_env_has u3 SHELLOPTS
  assert_false "u4 는 SHELLOPTS 를 받지 않음" svc_env_has u4 SHELLOPTS
  assert_false "u1 은 PS4 를 받지 않음" svc_env_has u1 PS4
  assert_false "u4 는 PS4 를 받지 않음" svc_env_has u4 PS4
}

case_secret_not_in_output_logs_args() {
  new_scenario env-nosecret-out
  export PAYMENT_WEBHOOK_SECRET="$FAKE_SECRET_32"
  start_all_fakes
  assert_eq "$(svc_env_value u3 PAYMENT_WEBHOOK_SECRET)" "$FAKE_SECRET_32" "u3 가 비밀 값을 환경으로 받음"
  if secret_checks_enabled; then
    assert_not_contains "$(rl_text)" "$FAKE_SECRET_32" "화면 출력에 비밀 값 없음"
    assert_false "서비스 로그에 비밀 값 없음" grep -rqF -- "$FAKE_SECRET_32" "$WORK/root/.local-run/logs"
    assert_false "가짜 서비스가 받은 명령줄 인자에 비밀 값 없음" grep -qF -- "$FAKE_SECRET_32" "$WORK/state/u3.args"
    assert_false "u3 명령줄(ps)에 비밀 값 없음" bash -c "ps -o command= -p \$(cat '$WORK/state/u3.pid') | grep -qF -- '$FAKE_SECRET_32'"
  fi
}

case_start_service_group_id_is_job_id() {
  new_scenario env-pgid
  start_all_fakes
  local pids pid1 pgid1
  pids="$(cat "$RL_OUT")"
  pid1="$(echo "$pids" | cut -d' ' -f1)"
  pgid1="$(cat "$WORK/state/u1.pgid")"
  assert_eq "$pgid1" "$pid1" "u1 의 프로세스 그룹 ID 가 \$! 와 같음"
  assert_eq "$(cat "$WORK/state/u1.pid")" "$pid1" "기록된 서비스 PID 가 \$! 와 같음"
  assert_eq "$(cat "$WORK/state/u3.pgid")" "$(echo "$pids" | cut -d' ' -f3)" "u3 도 자기 그룹"
}

case_logs_are_private_and_overwritten() {
  new_scenario env-logs
  start_all_fakes
  local logf="$WORK/root/.local-run/logs/u1-search-service.log"
  wait_until 30 file_has "$logf" "fake u1"
  assert_eq "$(stat -f %Lp "$logf")" "600" "로그 파일 권한 0600"
  assert_eq "$(stat -f %Lp "$WORK/root/.local-run/logs")" "700" "로그 폴더 권한 0700"
  assert_eq "$(cat "$WORK/root/.local-run/.gitignore")" "*" ".local-run/.gitignore 내용은 *"
  local size1
  size1="$(wc -c < "$logf" | tr -d ' ')"
  kill_leftovers
  rm -f "$WORK"/state/*.env
  start_all_fakes
  wait_until 30 file_has "$logf" "fake u1"
  assert_eq "$(wc -c < "$logf" | tr -d ' ')" "$size1" "두 번 실행해도 로그 크기가 누적되지 않음"
}

# ---------- 기동 흐름 (Step 9) ----------

started_services() {
  tr '\n' ' ' < "$WORK/state/order.log" 2>/dev/null | sed 's/ $//'
}

case_start_success_foreground() {
  new_scenario flow-success
  launch_run_local
  assert_true "모든 서비스 준비 완료 안내가 나옴" wait_for_text "모든 서비스가 준비되었습니다"
  assert_eq "$(started_services)" "u1 u2 u3 u4" "기동 순서는 u1 u2 u3 u4"
  assert_true "접속 주소(화면 포트)를 알림" rl_has "http://127.0.0.1:$P4/"
  assert_true "u1 준비 시간을 알림" rl_has "u1-search-service 준비 완료 ("
  assert_true "u4 준비 시간을 알림" rl_has "u4-web-ui 준비 완료 ("
  sleep 2
  assert_true "준비 뒤에도 포그라운드로 계속 실행" pid_alive "$RL_PID"
  assert_true "Ctrl-C 로 정지한다는 안내" rl_has "Ctrl-C"
  assert_false "Docker 를 부르지 않음" grep -qs docker "$WORK/state/calls.log"
  assert_true "u4 포트가 실제로 응답(가짜 UI)" curl -s -o /dev/null --max-time 5 "http://127.0.0.1:$P4/"
}

case_start_waits_for_each_service_in_order() {
  new_scenario flow-order
  launch_run_local
  wait_for_text "모든 서비스가 준비되었습니다"
  local t1 t2 t3 t4
  t1="$(grep -n 'u1-search-service 준비 완료' "$RL_OUT" | cut -d: -f1)"
  t2="$(grep -n 'u2-reservation-service 준비 완료' "$RL_OUT" | cut -d: -f1)"
  t3="$(grep -n 'u3-payment-service 준비 완료' "$RL_OUT" | cut -d: -f1)"
  t4="$(grep -n 'u4-web-ui 준비 완료' "$RL_OUT" | cut -d: -f1)"
  assert_true "준비 완료 안내가 u1 u2 u3 u4 순서" test "$t1" -lt "$t2" -a "$t2" -lt "$t3" -a "$t3" -lt "$t4"
}

case_port_in_use_refused() {
  new_scenario flow-portbusy
  start_listener "$P2"
  run_rl_sync
  assert_true "사용 중인 포트가 있으면 0이 아닌 종료 코드" test "$RL_RC" -ne 0
  assert_true "어느 포트인지 알림 ($P2)" rl_has "$P2"
  assert_true "해당 서비스 이름도 알림" rl_has "u2-reservation-service"
  assert_false "서비스를 하나도 띄우지 않음" test -f "$WORK/state/order.log"
}

case_not_prepared_refused() {
  new_scenario flow-notprepared
  rm -rf "$WORK/root/services/u2-reservation-service/target"
  run_rl_sync
  assert_true "준비되지 않은 빌드는 0이 아닌 종료 코드" test "$RL_RC" -ne 0
  assert_true "--prepare 를 안내" rl_has "--prepare"
  assert_true "어느 서비스인지 알림" rl_has "u2-reservation-service"
  assert_false "서비스를 하나도 띄우지 않음" test -f "$WORK/state/order.log"
  rm -rf "$WORK/root/services/u4-web-ui/node_modules"
  mkdir -p "$WORK/root/services/u2-reservation-service/target/classes"
  run_rl_sync
  assert_true "node_modules 가 없으면 u4 를 알림" rl_has "u4-web-ui"
}

case_service_exits_immediately() {
  new_scenario flow-exit
  export FAKE_MODE_U2=exit
  run_rl_sync
  assert_true "서비스가 즉시 종료하면 0이 아닌 종료 코드" test "$RL_RC" -ne 0
  assert_true "어느 서비스인지 알림" rl_has "u2-reservation-service"
  assert_true "종료 사유를 알림" rl_has "종료되었습니다"
  assert_eq "$(rl_text | grep -c 'fake-u2 로그 줄')" "20" "로그 마지막 20줄만 보임"
  assert_true "마지막 줄(30)이 보임" rl_has "fake-u2 로그 줄 30"
  assert_eq "$(started_services)" "u1 u2" "u2 에서 멈추고 u3 u4 는 띄우지 않음"
}

case_ready_timeout_reports() {
  new_scenario flow-timeout
  export FAKE_MODE_U1=no-listen RUN_LOCAL_READY_TIMEOUT=3
  run_rl_sync
  assert_true "준비 시간 초과는 0이 아닌 종료 코드" test "$RL_RC" -ne 0
  assert_true "준비 시간 초과 사유" rl_has "준비 시간 초과"
  assert_true "어느 서비스인지 알림" rl_has "u1-search-service"
  assert_true "로그를 보여 줌" rl_has "fake u1 시작"
  assert_eq "$(started_services)" "u1" "u1 만 띄움"
}

case_health_body_not_up_is_not_ready() {
  new_scenario flow-down
  export FAKE_MODE_U1=down RUN_LOCAL_READY_TIMEOUT=3
  run_rl_sync
  assert_true "status 가 UP 이 아니면 준비로 보지 않음" rl_has "준비 시간 초과"
  assert_eq "$(started_services)" "u1" "다음 서비스로 넘어가지 않음"
}

# u4 준비 판정: / 가 200 이고 프록시 점검(POST /api/search/recommendations, 본문 {})이 정확히 400 일 때만 준비
probe_u4_now() {
  rl_eval 'load_config; if probe_ready u4; then printf "READY\n"; else printf "NOTREADY\n"; fi'
  cat "$RL_OUT"
}

case_u4_ready_only_on_exact_400() {
  new_scenario flow-u4probe
  mkdir -p "$WORK/ctl"
  export FAKE_CTL_DIR="$WORK/ctl"
  FAKE_ROLE=u4 FAKE_PORT="$P4" node "$FAKES_DIR/fake-server.js" &
  echo $! >> "$WORK/state/listener.pid"
  wait_until 30 port_open "$P4"
  printf '400' > "$WORK/ctl/fake_recs_status"
  assert_eq "$(probe_u4_now)" "READY" "/ 200 과 프록시 점검 400 이면 준비"
  local code
  for code in 500 502 404 200 401; do
    printf '%s' "$code" > "$WORK/ctl/fake_recs_status"
    assert_eq "$(probe_u4_now)" "NOTREADY" "프록시 점검이 $code 이면 준비 아님"
  done
  printf '400' > "$WORK/ctl/fake_recs_status"
  printf '500' > "$WORK/ctl/fake_root_status"
  assert_eq "$(probe_u4_now)" "NOTREADY" "/ 가 200 이 아니면 준비 아님"
  assert_file_has "$WORK/ctl/requests.log" "ct=application/json" "점검 요청에 Content-Type 헤더"
  assert_true "점검 요청에 UUID 형식 X-Session-Id" grep -Eq 'sid=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' "$WORK/ctl/requests.log"
  kill_leftovers
  assert_true "서버 종료를 기다림" wait_until 30 port_closed "$P4"
  assert_eq "$(probe_u4_now)" "NOTREADY" "연결 거부면 준비 아님"
}

case_u1_health_probe_requires_up_body() {
  new_scenario flow-healthprobe
  mkdir -p "$WORK/ctl"
  export FAKE_CTL_DIR="$WORK/ctl"
  FAKE_ROLE=u1 FAKE_PORT="$P1" node "$FAKES_DIR/fake-server.js" &
  echo $! >> "$WORK/state/listener.pid"
  wait_until 30 port_open "$P1"
  rl_eval 'load_config; if probe_ready u1; then printf "READY\n"; else printf "NOTREADY\n"; fi'
  assert_eq "$(cat "$RL_OUT")" "READY" "본문에 status UP 이 있으면 준비"
  printf 'DOWN' > "$WORK/ctl/fake_health"
  rl_eval 'load_config; if probe_ready u1; then printf "READY\n"; else printf "NOTREADY\n"; fi'
  assert_eq "$(cat "$RL_OUT")" "NOTREADY" "본문 status 가 DOWN 이면 준비 아님"
}

case_session_uuid_has_fallback() {
  new_light flow-uuid
  rl_eval 'new_uuid'
  assert_true "uuidgen 이 있으면 소문자 UUID" grep -Eq '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' "$RL_OUT"
  printf '#!/bin/bash\nexit 1\n' > "$WORK/fake-bin/uuidgen"
  chmod +x "$WORK/fake-bin/uuidgen"
  export PATH="$WORK/fake-bin:$PATH"
  rl_eval 'new_uuid'
  assert_true "uuidgen 이 없으면 od 로 만든 UUID 형식" grep -Eq '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-a[0-9a-f]{3}-[0-9a-f]{12}$' "$RL_OUT"
}

case_help_lists_options() {
  new_light flow-help
  run_rl_sync --help
  assert_eq "$RL_RC" "0" "--help 는 0으로 끝남"
  local v
  for v in U1_PORT U2_PORT U3_PORT SERVER_ADDRESS UI_HOST PAYMENT_WEBHOOK_SECRET RUN_LOCAL_READY_TIMEOUT RUN_LOCAL_TERM_GRACE RUN_LOCAL_WATCH_INTERVAL "--prepare" "--help" "Ctrl-C"; do
    assert_true "--help 에 $v" rl_has "$v"
  done
  assert_true "기본값 8081 이 보임" rl_has "8081"
  assert_true "기본값 8082 가 보임" rl_has "8082"
  assert_true "기본값 8083 이 보임" rl_has "8083"
  assert_true "고정 화면 포트 5173 이 보임" rl_has "5173"
  assert_true "준비 제한 기본값 90초" rl_has "90"
}

case_unknown_argument_prints_usage() {
  new_light flow-badarg
  run_rl_sync --bogus
  assert_true "알 수 없는 인자는 0이 아닌 종료 코드" test "$RL_RC" -ne 0
  assert_true "사용법을 보임" rl_has "--prepare"
  run_rl_sync --help extra
  assert_true "인자가 둘 이상이면 0이 아닌 종료 코드" test "$RL_RC" -ne 0
  assert_true "인자가 둘 이상이면 사용법을 보임" rl_has "사용법"
}

case_prepare_runs_build_tools() {
  new_scenario flow-prepare
  rm -rf "$WORK/root/services/u1-search-service/target"
  run_rl_sync --prepare
  assert_eq "$RL_RC" "0" "--prepare 성공"
  assert_file_has "$WORK/state/calls.log" "mvn -q -DskipTests compile" "컴파일 호출"
  assert_file_has "$WORK/state/calls.log" "npm --prefix services/u4-web-ui install" "npm install 호출"
  assert_false "--prepare 는 서비스를 띄우지 않음" test -f "$WORK/state/order.log"
  export FAKE_OTHER_RC=1
  run_rl_sync --prepare
  assert_true "도구가 실패하면 0이 아닌 종료 코드" test "$RL_RC" -ne 0
  assert_true "실패를 알림" rl_has "준비"
}

# ---------- 종료와 감시 (Step 12) ----------

start_and_wait_ready() {
  launch_run_local
  wait_for_text "모든 서비스가 준비되었습니다"
}

assert_nothing_left() {
  assert_true "$1: 자식·손자 포함 모든 프로세스가 없음" wait_until "${T_NOTHING:-30}" all_pids_gone
  assert_true "$1: 네 포트가 모두 닫힘" wait_until "${T_NOTHING:-30}" all_ports_closed
}

case_stop_signals_clean_everything() {
  local sig expected_rc
  for sig in INT TERM HUP; do
    case "$sig" in
      INT) expected_rc=130 ;;
      TERM) expected_rc=143 ;;
      HUP) expected_rc=129 ;;
    esac
    new_scenario "stop-$sig"
    start_and_wait_ready
    assert_true "$sig: 기동 완료" rl_has "모든 서비스가 준비되었습니다"
    assert_true "$sig: 정리 전에는 서비스 프로세스가 있음" test -n "$(all_service_pids)"
    kill "-$sig" "$RL_PID"
    wait_run_local
    assert_eq "$RL_RC" "$expected_rc" "$sig: 종료 코드 $expected_rc"
    assert_true "$sig: 정리 완료 안내" rl_has "정리 완료"
    assert_nothing_left "$sig"
    end_scenario
  done
}

case_sigterm_ignored_needs_sigkill() {
  new_scenario stop-sigkill
  export FAKE_MODE_U3=ignore-term RUN_LOCAL_TERM_GRACE=2
  start_and_wait_ready
  local t0="$SECONDS"
  kill -INT "$RL_PID"
  wait_run_local
  assert_true "유예(2초)를 기다린 뒤 끝남" test $((SECONDS - t0)) -ge 2
  assert_true "SIGKILL 로 강제 종료했음을 알림" rl_has "SIGKILL"
  assert_nothing_left "SIGTERM 무시"
}

case_second_signal_during_cleanup() {
  new_scenario stop-twice
  export FAKE_MODE_U3=ignore-term RUN_LOCAL_TERM_GRACE=4
  start_and_wait_ready
  kill -INT "$RL_PID"
  wait_for_text "정리 시작" 30
  kill -INT "$RL_PID" 2>/dev/null
  kill -TERM "$RL_PID" 2>/dev/null
  kill -HUP "$RL_PID" 2>/dev/null
  wait_run_local
  assert_eq "$RL_RC" "130" "첫 신호의 종료 코드로 끝남"
  assert_eq "$(rl_text | grep -c '정리 완료')" "1" "정리가 한 번만 끝남"
  assert_eq "$(rl_text | grep -c '정리 시작')" "1" "정리가 한 번만 시작됨"
  assert_nothing_left "두 번째 신호"
}

case_startup_failure_cleans_started_services() {
  new_scenario stop-startfail
  export FAKE_MODE_U2=exit
  run_rl_sync
  assert_true "기동 실패는 0이 아닌 종료 코드" test "$RL_RC" -ne 0
  assert_true "이미 띄운 u1 이 있었음" test -s "$WORK/state/u1.pids"
  assert_nothing_left "기동 실패"
  assert_true "정리 완료 안내" rl_has "정리 완료"
}

case_timeout_failure_cleans_started_services() {
  new_scenario stop-timeoutfail
  export FAKE_MODE_U3=no-listen RUN_LOCAL_READY_TIMEOUT=3
  run_rl_sync
  assert_true "준비 시간 초과는 0이 아닌 종료 코드" test "$RL_RC" -ne 0
  assert_nothing_left "준비 시간 초과"
}

case_watch_detects_service_exit() {
  new_scenario stop-watch
  start_and_wait_ready
  kill -9 "$(cat "$WORK/state/u3.pid")"
  wait_run_local
  assert_true "서비스 하나가 끝나면 0이 아닌 종료 코드" test "$RL_RC" -ne 0
  assert_true "어느 서비스인지 알림" rl_has "u3-payment-service"
  assert_true "종료를 알림" rl_has "종료되었습니다"
  assert_nothing_left "감시"
}

case_cleanup_tolerates_ended_groups() {
  new_light stop-ended
  rl_eval 'load_config; sleep 0 & p=$!; wait "$p"; SVC_NAMES=(u1); SVC_PIDS=("$p"); cleanup; printf "rc=%s\n" "$?"'
  assert_eq "$RL_RC" "0" "이미 끝난 그룹이 있어도 정리가 실패하지 않음"
  assert_contains "$(cat "$RL_OUT")" "rc=0" "cleanup 이 0을 돌려줌"
  assert_true "정리 완료 안내" rl_has "정리 완료"
}

case_cleanup_runs_only_once() {
  new_light stop-once
  rl_eval 'load_config; SVC_NAMES=(); SVC_PIDS=(); cleanup; cleanup; printf "done\n"'
  assert_eq "$RL_RC" "0" "정리를 두 번 불러도 성공"
  assert_true "정리 완료 안내가 최대 한 번" test "$(rl_text | grep -c '정리 완료')" -le 1
}

# ---------- main ----------

run_case case_cfg_defaults
run_case case_cfg_port_change_derives_urls
run_case case_cfg_u3_port_changes_webhook_url
run_case case_cfg_invalid_ports
run_case case_cfg_duplicate_ports
run_case case_cfg_rejects_non_loopback
run_case case_cfg_rejects_allow_non_loopback
run_case case_cfg_rejects_bad_timeouts
run_case case_secret_generated_long_enough
run_case case_secret_user_value_used
run_case case_secret_user_value_rejected
run_case case_secret_openssl_failure_uses_od
run_case case_secret_both_short_rejected
run_case case_env_matches_injection_table
run_case case_secret_only_reaches_u3
run_case case_user_shell_variables_not_inherited
run_case case_all_services_get_loopback
run_case case_shellopts_not_inherited
run_case case_secret_not_in_output_logs_args
run_case case_start_service_group_id_is_job_id
run_case case_logs_are_private_and_overwritten
run_case case_start_success_foreground
run_case case_start_waits_for_each_service_in_order
run_case case_port_in_use_refused
run_case case_not_prepared_refused
run_case case_service_exits_immediately
run_case case_ready_timeout_reports
run_case case_health_body_not_up_is_not_ready
run_case case_u4_ready_only_on_exact_400
run_case case_u1_health_probe_requires_up_body
run_case case_session_uuid_has_fallback
run_case case_help_lists_options
run_case case_unknown_argument_prints_usage
run_case case_prepare_runs_build_tools
run_case case_stop_signals_clean_everything
run_case case_sigterm_ignored_needs_sigkill
run_case case_second_signal_during_cleanup
run_case case_startup_failure_cleans_started_services
run_case case_timeout_failure_cleans_started_services
run_case case_watch_detects_service_exit
run_case case_cleanup_tolerates_ended_groups
run_case case_cleanup_runs_only_once

suite_end

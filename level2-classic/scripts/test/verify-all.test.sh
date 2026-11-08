#!/bin/bash
# verify-all.sh 단계 조합 시험. 가짜 mvn/npm 과 가짜 u5 시험 스크립트를 쓴다. /bin/bash 3.2 호환.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

suite_begin verify-all

VERIFY="$SCRIPTS_DIR/verify-all.sh"

# 가짜 u5 시험 스크립트: 실행 표시를 남기고 FAKE_RC_U5 로 끝난다 (진짜 run-all.sh 를 부르면 서로를 재귀로 부른다)
make_fake_u5() {
  printf '#!/bin/bash\necho ran >> "%s/state/u5.ran"\nexit "${FAKE_RC_U5:-0}"\n' "$WORK" > "$WORK/fake-u5.sh"
  chmod +x "$WORK/fake-u5.sh"
  export VERIFY_U5_SCRIPT="$WORK/fake-u5.sh"
}

run_verify() {
  VERIFY_RC=0
  ( cd "$WORK/root" && /bin/bash "$VERIFY" "$@" > "$WORK/verify-out.txt" 2> "$WORK/verify-err.txt" ) || VERIFY_RC=$?
  VERIFY_TEXT="$(cat "$WORK/verify-out.txt" "$WORK/verify-err.txt")"
}

prepare_verify() {
  new_light "$1"
  mkdir -p "$WORK/root"
  write_wrapper "$WORK/fake-bin/mvn" mvn
  write_wrapper "$WORK/fake-bin/npm" npm
  export PATH="$WORK/fake-bin:$PATH"
  export RUN_LOCAL_ROOT="$WORK/root"
  export VERIFY_LOAD_AVG=1.5
  make_fake_u5
}

case_verify_all_pass() {
  prepare_verify v-pass
  run_verify
  assert_eq "$VERIFY_RC" "0" "모든 단계가 통과하면 0"
  assert_file_has "$WORK/state/calls.log" "mvn -q verify" "mvn -q verify 호출"
  assert_file_has "$WORK/state/calls.log" "npm --prefix services/u4-web-ui run verify" "u4 verify 호출"
  assert_true "u5 시험·커버리지 단계 실행" test -f "$WORK/state/u5.ran"
  assert_contains "$VERIFY_TEXT" "통과 3/3" "요약에 통과 3/3"
}

case_verify_mvn_fails_but_all_steps_run() {
  prepare_verify v-mvnfail
  export FAKE_RC_MVN=1
  run_verify
  assert_true "mvn 실패면 0이 아닌 종료 코드" test "$VERIFY_RC" -ne 0
  assert_file_has "$WORK/state/calls.log" "npm --prefix services/u4-web-ui run verify" "mvn 이 실패해도 u4 단계 실행"
  assert_true "mvn 이 실패해도 u5 단계 실행" test -f "$WORK/state/u5.ran"
  assert_contains "$VERIFY_TEXT" "실패한 단계" "실패한 단계 요약"
  assert_contains "$VERIFY_TEXT" "mvn -q verify" "실패한 단계 이름"
  assert_contains "$VERIFY_TEXT" "통과 2/3" "요약에 통과 2/3"
}

case_verify_npm_fails() {
  prepare_verify v-npmfail
  export FAKE_RC_NPM=1
  run_verify
  assert_true "npm 실패면 0이 아닌 종료 코드" test "$VERIFY_RC" -ne 0
  assert_true "npm 이 실패해도 u5 단계 실행" test -f "$WORK/state/u5.ran"
  assert_contains "$VERIFY_TEXT" "u4 화면 검증" "실패한 단계 이름"
}

case_verify_u5_fails() {
  prepare_verify v-u5fail
  export FAKE_RC_U5=1
  run_verify
  assert_true "u5 실패면 0이 아닌 종료 코드" test "$VERIFY_RC" -ne 0
  assert_contains "$VERIFY_TEXT" "u5 시험과 커버리지" "실패한 단계 이름"
}

case_verify_all_fail() {
  prepare_verify v-allfail
  export FAKE_RC_MVN=1 FAKE_RC_NPM=1 FAKE_RC_U5=1
  run_verify
  assert_true "모두 실패해도 모든 단계를 실행" test -f "$WORK/state/u5.ran"
  assert_contains "$VERIFY_TEXT" "통과 0/3" "요약에 통과 0/3"
}

case_verify_does_not_retry() {
  prepare_verify v-noretry
  export FAKE_RC_MVN=1
  run_verify
  assert_eq "$(grep -c '^mvn -q verify' "$WORK/state/calls.log")" "1" "실패한 단계를 재실행하지 않음"
}

case_verify_slow_flag() {
  prepare_verify v-slow
  run_verify --slow
  assert_eq "$VERIFY_RC" "0" "--slow 통과"
  assert_file_has "$WORK/state/calls.log" "mvn -q verify -Pslow" "--slow 이면 -Pslow 전달"
  : > "$WORK/state/calls.log"
  run_verify
  assert_file_lacks "$WORK/state/calls.log" "-Pslow" "--slow 가 없으면 -Pslow 없음"
}

case_verify_load_warning() {
  prepare_verify v-load
  export VERIFY_LOAD_AVG=75.3
  run_verify
  assert_contains "$VERIFY_TEXT" "75.3" "부하 평균을 출력"
  assert_contains "$VERIFY_TEXT" "시간 시험이 흔들릴 수 있습니다" "60 이상이면 경고"
  assert_eq "$VERIFY_RC" "0" "경고만 하고 단계는 그대로 실행"
  export VERIFY_LOAD_AVG=60.0
  run_verify
  assert_contains "$VERIFY_TEXT" "시간 시험이 흔들릴 수 있습니다" "경계값 60 도 경고"
  export VERIFY_LOAD_AVG=59.9
  run_verify
  assert_not_contains "$VERIFY_TEXT" "시간 시험이 흔들릴 수 있습니다" "60 미만이면 경고 없음"
  assert_contains "$VERIFY_TEXT" "59.9" "부하 평균을 출력"
}

case_verify_reads_real_load_average() {
  prepare_verify v-realload
  unset VERIFY_LOAD_AVG
  run_verify
  assert_true "부하 평균 값을 출력" bash -c "echo '$VERIFY_TEXT' | grep -Eq '부하 평균.*[0-9]'"
}

case_verify_unknown_argument() {
  prepare_verify v-badarg
  run_verify --bogus
  assert_true "알 수 없는 인자는 0이 아닌 종료 코드" test "$VERIFY_RC" -ne 0
  assert_contains "$VERIFY_TEXT" "--slow" "사용법 안내"
}

run_case case_verify_all_pass
run_case case_verify_mvn_fails_but_all_steps_run
run_case case_verify_npm_fails
run_case case_verify_u5_fails
run_case case_verify_all_fail
run_case case_verify_does_not_retry
run_case case_verify_slow_flag
run_case case_verify_load_warning
run_case case_verify_reads_real_load_average
run_case case_verify_unknown_argument

suite_end

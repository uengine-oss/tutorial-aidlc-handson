#!/bin/bash
# xtrace 줄 커버리지 측정기(coverage.sh)와 bash 3.2 줄 번호 보존(카나리) 자체 시험. /bin/bash 3.2 호환.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

suite_begin coverage

COVERAGE_SH="$TEST_LIB_DIR/coverage.sh"
FIXTURES="$TEST_LIB_DIR/fixtures"
SAMPLE="$FIXTURES/cov-sample.sh"
CANARY="$FIXTURES/canary-sample.sh"

# 예제 스크립트를 xtrace 로 돌려 stderr 를 파일에 담는다: trace_run <출력 파일> <스크립트> <인자...>
# bash 3.2 가 99자에서 자르는 PS4 때문에 작업 폴더에서 짧은 상대 경로로 부른다 (lib.sh 의 RL_REL 설명 참고)
trace_run() {
  local out="$1" script="$2"
  shift 2
  case "$script" in
    "$TEST_LIB_DIR"/*)
      script="../../../scripts/test/${script#$TEST_LIB_DIR/}"
      ;;
    "$WORK"/*)
      script="./${script#$WORK/}"
      ;;
  esac
  ( cd "$WORK" && env SHELLOPTS=xtrace PS4="$COV_PS4" /bin/bash "$script" "$@" > /dev/null 2> "$out" ) || true
}

analyze() {
  AN_RC=0
  /bin/bash "$COVERAGE_SH" --analyze "$@" > "$WORK/analyze-out.txt" 2> "$WORK/analyze-err.txt" || AN_RC=$?
  AN_TEXT="$(cat "$WORK/analyze-out.txt" "$WORK/analyze-err.txt")"
}

traced_lines() {
  grep -o "TRACE|[^|]*$2|[0-9]*|" "$1" | cut -d'|' -f3 | sort -un | tr '\n' ' '
}

# ---------- 카나리 (G1): bash 3.2 에서 SHELLOPTS=xtrace + PS4 로 줄 번호가 보존되는가 ----------

case_canary_xtrace_keeps_line_numbers() {
  new_light canary
  assert_true "시험은 bash 3.2 에서 돈다" test "${BASH_VERSINFO[0]}" = "3"
  trace_run "$WORK/canary.err" "$CANARY"
  local lines child
  lines="$(traced_lines "$WORK/canary.err" canary-sample.sh)"
  child="$(traced_lines "$WORK/canary.err" canary-child.sh)"
  local n
  for n in 6 7 8; do
    assert_contains " $lines" " $n " "함수 본문 줄 $n 이 원래 줄 번호로 찍힘"
  done
  assert_contains " $lines" " 22 " "명령 치환 \$(...) 줄 22"
  assert_contains " $lines" " 23 " "서브셸 ( ... ) 줄 23"
  assert_contains " $lines" " 12 " "EXIT trap 처리기 안의 줄 12"
  assert_contains " $lines" " 16 " "신호 trap 처리기 안의 줄 16"
  assert_contains " $lines" " 26 " "case 로 들어간 분기 줄 26"
  assert_contains " $lines" " 33 " "if 로 들어간 분기 줄 33"
  assert_contains " $lines" " 38 " "for 반복 줄 38"
  assert_not_contains " $lines" " 29 " "실행하지 않은 case 분기 줄 29 는 안 찍힘"
  assert_not_contains " $lines" " 35 " "실행하지 않은 else 분기 줄 35 는 안 찍힘"
  assert_contains " $child" " 4 " "자식 bash 스크립트 줄 4 가 찍힘"
  assert_contains " $child" " 5 " "자식 bash 의 서브셸 줄 5 가 찍힘"
}

# ---------- 분모 규칙 (G1) ----------

case_denominator_rules() {
  new_light denom
  AN_RC=0
  /bin/bash "$COVERAGE_SH" --denominator "$SAMPLE" > "$WORK/analyze-out.txt" 2> "$WORK/analyze-err.txt" || AN_RC=$?
  assert_eq "$AN_RC" "0" "분모 계산 성공"
  local lines
  lines=" $(tr '\n' ' ' < "$WORK/analyze-out.txt")"
  local n
  for n in 3 6 7 8 10 15 17 20 26 27 32 38 39 42 43 44 45 46 47; do
    assert_contains "$lines" " $n " "명령 줄 $n 은 분모에 있음"
  done
  for n in 1 2 4 5 9 11 12 14 16 18 19 21 22 25 28 29 31 33 34 35 37 40 48; do
    assert_not_contains "$lines" " $n " "제외 대상 줄 $n (빈 줄, 주석, 함수 머리, fi/done/esac/}/;;/else, case 라벨, 이어쓴 뒷줄)"
  done
  assert_eq "$(wc -w < "$WORK/analyze-out.txt" | tr -d ' ')" "19" "분모는 19줄"
}

# ---------- 측정과 하한 ----------

case_below_floor_fails() {
  new_light below
  trace_run "$WORK/low.err" "$SAMPLE" a one
  analyze "$SAMPLE" "$WORK/low.err"
  assert_true "하한 80% 미만이면 0이 아닌 종료 코드" test "$AN_RC" -ne 0
  assert_contains "$AN_TEXT" "73.7%" "비율 73.7% (14/19)"
  assert_contains "$AN_TEXT" "14" "실행된 줄 수"
  assert_contains "$AN_TEXT" "19" "분모 줄 수"
  assert_contains "$AN_TEXT" "실행되지 않은 줄: 10 20 38 39 47" "실행되지 않은 줄 번호를 보임"
  assert_contains "$AN_TEXT" "80%" "하한을 보임"
}

case_at_or_above_floor_passes() {
  new_light above
  trace_run "$WORK/a.err" "$SAMPLE" a one
  trace_run "$WORK/b.err" "$SAMPLE" b two all
  analyze "$SAMPLE" "$WORK/a.err" "$WORK/b.err"
  assert_eq "$AN_RC" "0" "여러 실행을 합치면 하한을 넘어 통과"
  assert_contains "$AN_TEXT" "100.0%" "합친 비율 100.0% (19/19)"
  trace_run "$WORK/c.err" "$SAMPLE" b one
  analyze "$SAMPLE" "$WORK/a.err" "$WORK/c.err"
  assert_contains "$AN_TEXT" "78.9%" "한 줄만 더해도 정확히 계산 (15/19)"
  assert_true "78.9% 는 미달" test "$AN_RC" -ne 0
}

case_exactly_at_floor_passes() {
  new_light exact
  # 5줄짜리 예제: 4줄 실행 = 정확히 80%
  printf '#!/usr/bin/env bash\nprintf one\\n\nprintf two\\n\nprintf three\\n\nif false; then\n  printf four\\n\nfi\n' > "$WORK/five.sh"
  trace_run "$WORK/five.err" "$WORK/five.sh"
  analyze "$WORK/five.sh" "$WORK/five.err"
  assert_contains "$AN_TEXT" "80.0%" "정확히 80.0%"
  assert_eq "$AN_RC" "0" "정확히 80% 는 통과"
}

case_trace_parsing_is_robust() {
  new_light parse
  printf 'garbage prefix++TRACE|/x/cov-sample.sh|3|set -euo pipefail\n' > "$WORK/odd.err"
  printf '+TRACE|/x/other-file.sh|6|printf not counted\n' >> "$WORK/odd.err"
  printf '+TRACE|scripts/test/fixtures/cov-sample.sh|7|[ a = a ]\n' >> "$WORK/odd.err"
  printf 'plain stderr message without a trace marker 8\n' >> "$WORK/odd.err"
  printf '+TRACE|cov-sample.sh|3|duplicate hit counted once ++TRACE|cov-sample.sh|42|two on one line\n' >> "$WORK/odd.err"
  analyze "$SAMPLE" "$WORK/odd.err"
  assert_contains "$AN_TEXT" "3 of 19" "접두 문자열, 같은 줄의 두 기록, 중복을 올바르게 셈 (3, 7, 42 만 실행)"
}

case_truncated_trace_is_detected() {
  new_light truncated
  # bash 3.2 는 PS4 확장을 99자에서 자른다. 긴 경로에서 줄 번호가 사라진 기록은 조용히 넘기지 않고 실패로 알린다.
  printf '+TRACE|/Users/someone/a/very/long/project/path/that/goes/on/and/on/and/on/for/a/while/scripts/tset -euo pipefail\n' > "$WORK/cut.err"
  printf '+TRACE|cov-sample.sh|3|set -euo pipefail\n' >> "$WORK/cut.err"
  analyze "$SAMPLE" "$WORK/cut.err"
  assert_true "줄 번호가 잘린 기록이 있으면 0이 아닌 종료 코드" test "$AN_RC" -ne 0
  assert_contains "$AN_TEXT" "잘린 xtrace 기록 1줄" "잘린 기록 수를 알림"
}

case_no_trace_is_zero_percent() {
  new_light notrace
  : > "$WORK/empty.err"
  analyze "$SAMPLE" "$WORK/empty.err"
  assert_true "기록이 비어 있으면 0%로 실패" test "$AN_RC" -ne 0
  assert_contains "$AN_TEXT" "0.0%" "0.0% 표시"
  analyze "$SAMPLE" "$WORK/does-not-exist.err"
  assert_true "없는 기록 파일은 오류" test "$AN_RC" -ne 0
  analyze "$WORK/no-such-script.sh" "$WORK/empty.err"
  assert_true "없는 스크립트는 오류" test "$AN_RC" -ne 0
}

case_floor_cannot_be_lowered() {
  new_light floor
  trace_run "$WORK/low.err" "$SAMPLE" a one
  COVERAGE_FLOOR=0 MIN_COVERAGE=0 FLOOR=0 analyze "$SAMPLE" "$WORK/low.err"
  assert_true "환경 변수로 하한을 낮출 수 없음" test "$AN_RC" -ne 0
  analyze --min 0 "$SAMPLE" "$WORK/low.err"
  assert_true "--min 같은 하한 인자를 받지 않음" test "$AN_RC" -ne 0
  assert_file_has "$COVERAGE_SH" "FLOOR=80" "하한 상수는 80"
}

run_case case_canary_xtrace_keeps_line_numbers
run_case case_denominator_rules
run_case case_below_floor_fails
run_case case_at_or_above_floor_passes
run_case case_exactly_at_floor_passes
run_case case_trace_parsing_is_robust
run_case case_truncated_trace_is_detected
run_case case_no_trace_is_zero_percent
run_case case_floor_cannot_be_lowered

suite_end

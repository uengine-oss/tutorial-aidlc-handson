#!/bin/bash
# u5-local-run 전체 시험. 반드시 /bin/bash(macOS 3.2)로 실행한다: /bin/bash scripts/test/run-all.sh
# 순서: 정적 검사(bash -n, shellcheck 있으면, 비밀 값 패턴) -> run-local 시나리오 -> 스모크 스크립트 시험 -> 전체 검증 스크립트 시험
#       -> 커버리지 측정기 자체 시험(카나리 포함) -> 커버리지 측정과 80% 하한.
# 한 단계가 실패해도 나머지 단계를 모두 실행하고 마지막에 요약한다. 하나라도 실패하면 0이 아닌 종료 코드로 끝난다.
TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
WORK_DIR="$TEST_DIR/../../.local-run/run-all-work"
set -o pipefail

mkdir -p "$WORK_DIR"
STEP_LINES=""
OVERALL=0

run_step() {
  local name="$1" out rc
  shift
  out="$WORK_DIR/step-$$-$RANDOM.txt"
  printf '\n######## %s ########\n' "$name"
  "$@" 2>&1 | tee "$out"
  rc=${PIPESTATUS[0]}
  STEP_LINES="$STEP_LINES
$rc|$name|$(grep -E '^RESULT |^커버리지: ' "$out" | tail -n 1)"
  if [ "$rc" != "0" ]; then
    OVERALL=1
  fi
  rm -f "$out"
  return 0
}

printf 'bash: %s\n' "$(/bin/bash -c 'echo $BASH_VERSION')"
if [ "$(/bin/bash -c 'echo ${BASH_VERSINFO[0]}')" != "3" ]; then
  printf '경고: /bin/bash 가 3.x 가 아닙니다. 이 시험은 macOS 기본 bash 3.2 에서 돌도록 만들었습니다.\n'
fi

run_step "정적 검사 (bash -n, 3.2 호환, 비밀 값 패턴, shellcheck 있으면)" /bin/bash "$TEST_DIR/static.test.sh"
run_step "run-local.sh 시나리오 시험" /bin/bash "$TEST_DIR/run-local.test.sh"
run_step "smoke-local.sh 시험" /bin/bash "$TEST_DIR/smoke-local.test.sh"
run_step "verify-all.sh 시험" /bin/bash "$TEST_DIR/verify-all.test.sh"
run_step "커버리지 측정기 자체 시험(카나리 포함)" /bin/bash "$TEST_DIR/coverage.test.sh"
run_step "run-local.sh 줄 커버리지 80% 하한" /bin/bash "$TEST_DIR/coverage.sh"

printf '\n######## 요약 ########\n'
total_cases=0
total_asserts=0
total_failed=0
IFS='
'
for line in $STEP_LINES; do
  [ -n "$line" ] || continue
  rc="${line%%|*}"
  rest="${line#*|}"
  name="${rest%%|*}"
  detail="${rest#*|}"
  if [ "$rc" = "0" ]; then mark="통과"; else mark="실패(종료 코드 $rc)"; fi
  printf '  [%s] %s\n         %s\n' "$mark" "$name" "$detail"
  case "$detail" in
    RESULT*)
      c="$(printf '%s' "$detail" | sed -n 's/.*: \([0-9]*\) cases, \([0-9]*\) assertions passed, \([0-9]*\) failed.*/\1/p')"
      a="$(printf '%s' "$detail" | sed -n 's/.*: \([0-9]*\) cases, \([0-9]*\) assertions passed, \([0-9]*\) failed.*/\2/p')"
      f="$(printf '%s' "$detail" | sed -n 's/.*: \([0-9]*\) cases, \([0-9]*\) assertions passed, \([0-9]*\) failed.*/\3/p')"
      total_cases=$((total_cases + ${c:-0}))
      total_asserts=$((total_asserts + ${a:-0}))
      total_failed=$((total_failed + ${f:-0}))
      ;;
  esac
done
unset IFS
printf '\n합계: 시험 %s개(case), 확인 %s개 통과, 실패 %s개\n' "$total_cases" "$total_asserts" "$total_failed"
if [ "$OVERALL" = "0" ]; then
  printf '전체 결과: 통과\n'
else
  printf '전체 결과: 실패\n'
fi
rm -rf "$WORK_DIR"
exit "$OVERALL"

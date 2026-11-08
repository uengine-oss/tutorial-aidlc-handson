#!/usr/bin/env bash
set -euo pipefail

# 병합 전 검증을 한 번에 실행한다: mvn -q verify [-Pslow], u4 화면 검증, u5 시험과 커버리지.
# 하나가 실패해도 나머지 단계를 모두 실행하고 실패한 단계를 요약한다. 간헐 실패를 가리지 않으려고 재실행하지 않는다 (G10).
# 프로젝트의 Construction Verification Command 를 이 스크립트로 바꾸는 일은 Unit 체크포인트에서 사람이 승인한다.

# ===== 설정 구역 =====
LOAD_WARN_AT=60
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${RUN_LOCAL_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
VERIFY_U5_SCRIPT="${VERIFY_U5_SCRIPT:-$SCRIPT_DIR/test/run-all.sh}"
SLOW=0
STEP_COUNT=0
PASSED=0
FAILED_NAMES=""

usage() {
  printf '%s\n' "사용법: scripts/verify-all.sh [--slow]"
  printf '%s\n' "  --slow   mvn verify 에 -Pslow 를 더해 u2 의 실제 180초 취소 시험까지 실행합니다."
}

# 부하 평균(1분)을 읽는다. 시험은 VERIFY_LOAD_AVG 로 값을 준다.
load_average() {
  local out value
  if [ -n "${VERIFY_LOAD_AVG:-}" ]; then
    printf '%s\n' "$VERIFY_LOAD_AVG"
    return 0
  fi
  out="$(uptime 2>/dev/null || true)"
  value="$(printf '%s' "$out" | sed 's/.*load averages*: *//' | tr ',' ' ' | awk '{print $1}')"
  printf '%s\n' "$value"
}

report_load() {
  local load int
  load="$(load_average)"
  if [ -z "$load" ]; then
    printf '부하 평균: 알 수 없음\n'
    return 0
  fi
  printf '부하 평균(1분): %s\n' "$load"
  int="${load%%.*}"
  case "$int" in
    ''|*[!0-9]*)
      return 0
      ;;
  esac
  if [ "$int" -ge "$LOAD_WARN_AT" ]; then printf '경고: 부하 평균이 %s 이상입니다. 시간 시험이 흔들릴 수 있습니다 (실패해도 재실행으로 가리지 않습니다).\n' "$LOAD_WARN_AT"; fi
}

step_mvn() {
  ( cd "$ROOT_DIR" && mvn -q verify )
}

step_mvn_slow() {
  ( cd "$ROOT_DIR" && mvn -q verify -Pslow )
}

step_npm() {
  ( cd "$ROOT_DIR" && npm --prefix services/u4-web-ui run verify )
}

step_u5() {
  ( cd "$ROOT_DIR" && /bin/bash "$VERIFY_U5_SCRIPT" )
}

run_step() {
  local name="$1"
  shift
  STEP_COUNT=$((STEP_COUNT + 1))
  printf '\n== 단계 %s: %s\n' "$STEP_COUNT" "$name"
  if "$@"; then
    printf '[통과] %s\n' "$name"
    PASSED=$((PASSED + 1))
  else
    printf '[실패] %s\n' "$name"
    FAILED_NAMES="$FAILED_NAMES|$name"
  fi
}

main() {
  local mvn_name="mvn -q verify"
  case "${1:-}" in
    '')
      SLOW=0
      ;;
    --slow)
      SLOW=1
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
  if [ "$#" -gt 1 ]; then
    usage >&2
    exit 2
  fi
  report_load
  if [ "$SLOW" = "1" ]; then
    run_step "mvn -q verify -Pslow" step_mvn_slow
  else
    run_step "$mvn_name" step_mvn
  fi
  run_step "u4 화면 검증 (npm run verify)" step_npm
  run_step "u5 시험과 커버리지" step_u5
  printf '\n== 요약\n'
  printf '통과 %s/%s\n' "$PASSED" "$STEP_COUNT"
  if [ -n "$FAILED_NAMES" ]; then
    printf '실패한 단계:%s\n' "$(printf '%s' "$FAILED_NAMES" | tr '|' '\n' | sed 's/^/\n  - /' | tr -d '\n')"
    exit 1
  fi
  printf '모든 단계가 통과했습니다.\n'
}

main "$@"

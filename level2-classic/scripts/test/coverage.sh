#!/bin/bash
# xtrace 기반 줄 커버리지 측정기와 80% 하한 검사. /bin/bash(3.2) 호환, 설치가 필요 없다.
#
#   /bin/bash scripts/test/coverage.sh
#       run-local.test.sh 의 모든 시나리오를 COVERAGE=1(SHELLOPTS=xtrace, PS4='+TRACE|${BASH_SOURCE}|${LINENO}|')로 돌려
#       scripts/run-local.sh 의 줄별 실행 여부를 모으고 비율을 계산한다. 하한 미달일 때만 0이 아닌 종료 코드로 끝난다.
#   /bin/bash scripts/test/coverage.sh --denominator <스크립트>
#       분모(명령이 있는 줄 번호)를 한 줄에 하나씩 출력한다.
#   /bin/bash scripts/test/coverage.sh --analyze <스크립트> <xtrace 기록 파일>...
#       주어진 기록으로 비율을 계산하고 하한을 검사한다 (이 측정기 자체의 시험용).
#
# 하한은 80% 이며 낮출 수 없다: 환경 변수나 인자로 바꾸는 길이 없다.
# 분모 제외 규칙(G1)은 이 파일의 denominator 함수 한곳에만 둔다:
#   빈 줄, 주석 줄, fi/done/esac/}/;;/else/then/do 만 있는 줄, 함수 머리줄(name() {), case 라벨 줄(pattern)),
#   그리고 \ 로 이어진 뒤쪽 줄. 규칙을 더하는 일은 사람의 승인이 필요하다.
# 근사 측정이다: 한 줄 안의 조건 분기는 세지 못하고, 한 번 지나가면 그 줄은 실행된 것으로 센다.
FLOOR=80

TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$TEST_DIR/../.." && pwd)"

denominator() {
  LC_ALL=C awk '
    BEGIN { cont = 0 }
    {
      line = $0
      if (cont == 1) {
        if (line !~ /\\[ \t]*$/) cont = 0
        next
      }
      if (line ~ /^[ \t]*$/) next
      if (line ~ /^[ \t]*#/) next
      if (line ~ /^[ \t]*(fi|done|esac|\}|;;|else|then|do)[ \t]*(;;)?[ \t]*(#.*)?$/) next
      if (line ~ /^[ \t]*[A-Za-z_][A-Za-z0-9_]*\(\)[ \t]*\{[ \t]*$/) next
      if (line ~ /^[ \t]*[^ \t(][^()]*\)[ \t]*$/) next
      print NR
      if (line ~ /\\[ \t]*$/) cont = 1
    }
  ' "$1"
}

# 기록 파일들에서 대상 스크립트(파일 이름이 같은 것)의 줄 번호를 중복 없이 뽑는다.
covered_lines() {
  local base="$1"
  shift
  LC_ALL=C awk -v base="$base" '
    {
      rest = $0
      while (match(rest, /\+TRACE\|[^|]*\|[0-9]+\|/)) {
        rec = substr(rest, RSTART, RLENGTH)
        rest = substr(rest, RSTART + RLENGTH)
        split(rec, p, "|")
        m = split(p[2], parts, "/")
        if (parts[m] == base) print p[3]
      }
    }
  ' "$@" | sort -un
}

analyze() {
  local script="$1" file n_total n_covered pct missing truncated all_marks ok_marks
  shift
  if [ ! -f "$script" ]; then
    printf '오류: 스크립트를 찾을 수 없습니다: %s\n' "$script" >&2
    return 2
  fi
  for file in "$@"; do
    if [ ! -f "$file" ]; then
      printf '오류: xtrace 기록 파일을 찾을 수 없습니다: %s\n' "$file" >&2
      return 2
    fi
  done
  local work
  work="$PROJECT_DIR/.local-run/coverage-work/analyze-$$"
  mkdir -p "$work"
  denominator "$script" > "$work/denominator.txt"
  covered_lines "$(basename "$script")" "$@" > "$work/covered.txt"
  # 분모에 있는 줄만 센다 (함수 정의 밖의 기록이나 파일 끝 너머의 줄은 제외)
  grep -x -F -f "$work/denominator.txt" "$work/covered.txt" > "$work/hit.txt" || true
  n_total="$(grep -c . "$work/denominator.txt" || true)"
  n_covered="$(grep -c . "$work/hit.txt" || true)"
  missing="$(grep -x -v -F -f "$work/hit.txt" "$work/denominator.txt" | tr '\n' ' ' | sed 's/ $//' || true)"
  if [ -s "$work/hit.txt" ]; then
    :
  else
    missing="$(tr '\n' ' ' < "$work/denominator.txt" | sed 's/ $//')"
  fi
  pct="$(awk -v c="$n_covered" -v t="$n_total" 'BEGIN { if (t == 0) printf "0.0"; else printf "%.1f", c * 100 / t }')"
  all_marks="$(cat "$@" | LC_ALL=C grep -a -c -E '\+TRACE\|' || true)"
  ok_marks="$(cat "$@" | LC_ALL=C grep -a -c -E '\+TRACE\|[^|]*\|[0-9]+\|' || true)"
  truncated=$((all_marks - ok_marks))
  printf '커버리지: %s%% (%s of %s 명령 줄, 하한 %s%%)\n' "$pct" "$n_covered" "$n_total" "$FLOOR"
  printf '실행되지 않은 줄: %s\n' "$missing"
  printf '분모 줄 수: %s\n' "$n_total"
  rm -rf "$work"
  if [ "$truncated" -gt 0 ]; then
    printf '오류: 잘린 xtrace 기록 %s줄이 있습니다. bash 3.2 는 PS4 확장을 99자에서 자르므로 스크립트를 짧은 상대 경로로 실행해야 줄 번호가 남습니다.\n' "$truncated" >&2
    return 3
  fi
  if [ "$n_total" -eq 0 ] || [ $((n_covered * 100)) -lt $((FLOOR * n_total)) ]; then
    printf '실패: 줄 커버리지가 하한 %s%% 미만입니다.\n' "$FLOOR" >&2
    return 1
  fi
  printf '통과: 줄 커버리지가 하한 %s%% 이상입니다.\n' "$FLOOR"
  return 0
}

run_full() {
  local work test_rc=0 rc=0
  work="$PROJECT_DIR/.local-run/coverage-work"
  rm -rf "$work"
  umask 077
  mkdir -p "$work/traces"
  printf 'run-local.sh 시나리오를 xtrace 모드(COVERAGE=1)로 실행합니다. 비밀 값 비출력 확인은 일반 실행에서만 합니다.\n'
  COVERAGE=1 COVERAGE_TRACE_DIR="$work/traces" /bin/bash "$TEST_DIR/run-local.test.sh" > "$work/run.txt" 2>&1 || test_rc=$?
  grep '^RESULT' "$work/run.txt" || true
  if [ "$test_rc" -ne 0 ]; then
    printf '주의: xtrace 모드의 시나리오 시험에서 실패가 있었습니다(커버리지 판정과 별개). 일반 실행 결과를 확인하세요.\n'
    grep '^  FAIL' "$work/run.txt" | head -n 20 || true
  fi
  cat "$work"/traces/* > "$work/all.trace" 2>/dev/null || : > "$work/all.trace"
  analyze "$PROJECT_DIR/scripts/run-local.sh" "$work/all.trace" || rc=$?
  if [ "${KEEP_COVERAGE_WORK:-0}" != "1" ]; then
    rm -rf "$work"
  fi
  return "$rc"
}

case "${1:-}" in
  --denominator)
    if [ ! -f "${2:-}" ]; then
      printf '오류: 스크립트를 찾을 수 없습니다: %s\n' "${2:-}" >&2
      exit 2
    fi
    denominator "$2"
    ;;
  --analyze)
    if [ "$#" -lt 3 ]; then
      printf '사용법: coverage.sh --analyze <스크립트> <xtrace 기록 파일>...\n' >&2
      exit 2
    fi
    shift
    analyze "$@"
    exit $?
    ;;
  '')
    run_full
    exit $?
    ;;
  *)
    printf '알 수 없는 인자: %s\n사용법: coverage.sh [--denominator <스크립트> | --analyze <스크립트> <기록>...]\n' "$1" >&2
    exit 2
    ;;
esac

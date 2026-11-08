#!/bin/bash
# 정적 검사: bash -n, bash 3.2 호환, 스타일 규칙(G1, NFR6.4, NFR9.1), 하드코딩된 비밀 값 검색, shellcheck(있으면). /bin/bash 3.2 호환.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"

suite_begin static

PRODUCT_SCRIPTS="$SCRIPTS_DIR/run-local.sh $SCRIPTS_DIR/smoke-local.sh $SCRIPTS_DIR/verify-all.sh"
ALL_SCRIPTS="$PRODUCT_SCRIPTS $(ls "$TEST_LIB_DIR"/*.sh "$TEST_LIB_DIR"/fakes/*.sh "$TEST_LIB_DIR"/fixtures/*.sh)"

# 하드코딩된 비밀 값 패턴을 찾는다. 인자: 파일... 출력: 걸린 줄. 시험용 고정 가짜 값(test-only-fixed-fake)만 허용한다.
scan_secrets() {
  grep -n -E "(SECRET|PASSWORD|PASSWD|TOKEN|API_KEY|PRIVATE_KEY)[A-Z_]*=[\"']?[A-Za-z0-9+/=_-]{16,}|[0-9a-fA-F]{64}|BEGIN [A-Z ]*PRIVATE KEY|AKIA[0-9A-Z]{16}" "$@" 2>/dev/null | grep -v 'test-only-fixed-fake' || true
}

case_bash_syntax_check() {
  new_light static-syntax
  local f bad=""
  for f in $ALL_SCRIPTS; do
    if ! /bin/bash -n "$f" 2> "$WORK/syntax.err"; then
      bad="$bad $f"
    fi
  done
  assert_eq "$bad" "" "모든 스크립트가 bash -n 을 통과"
  assert_true "시험은 /bin/bash 3.2 로 실행" test "${BASH_VERSINFO[0]}.${BASH_VERSINFO[1]}" = "3.2"
}

case_scripts_run_on_bash_3_2() {
  new_light static-bash32
  local v
  v="$(/bin/bash -c 'echo ${BASH_VERSINFO[0]}.${BASH_VERSINFO[1]}')"
  assert_eq "$v" "3.2" "/bin/bash 는 3.2"
  RL_OUT="$WORK/help.out"
  /bin/bash "$RUN_LOCAL" --help > "$RL_OUT" 2> "$WORK/help.err"
  assert_eq "$?" "0" "run-local.sh --help 가 3.2 에서 동작"
  /bin/bash "$SCRIPTS_DIR/verify-all.sh" --bogus > /dev/null 2>&1
  assert_eq "$?" "2" "verify-all.sh 가 3.2 에서 인자 오류를 종료 코드 2 로 알림"
  /bin/bash "$SCRIPTS_DIR/smoke-local.sh" --bogus > /dev/null 2>&1
  assert_eq "$?" "2" "smoke-local.sh 가 3.2 에서 인자 오류를 종료 코드 2 로 알림"
}

case_no_bash4_features() {
  new_light static-bash4
  local hits
  # 이 파일 자신은 검색 패턴을 담고 있어 제외한다
  hits="$(grep -n -E 'declare +-A|local +-A|typeset +-A|mapfile|readarray|coproc|BASH_XTRACEFD|\$\{[A-Za-z_0-9]+(,,|\^\^)\}|\|&|&>>|\[\[ +-v ' $ALL_SCRIPTS 2>/dev/null | grep -v 'static.test.sh' || true)"
  assert_eq "$hits" "" "bash 4 기능(연관 배열, mapfile, 대소문자 변환, |&, BASH_XTRACEFD)을 쓰지 않음"
}

case_product_script_conventions() {
  new_light static-style
  local f
  for f in $PRODUCT_SCRIPTS; do
    assert_eq "$(head -n 1 "$f")" "#!/usr/bin/env bash" "$(basename "$f"): 셰뱅 #!/usr/bin/env bash"
    assert_true "$(basename "$f"): set -euo pipefail" grep -q '^set -euo pipefail$' "$f"
    assert_true "$(basename "$f"): 실행 비트" test -x "$f"
    assert_eq "$(grep -n -E '<<' "$f" | grep -v '\$((' || true)" "" "$(basename "$f"): here-doc 없음"
    assert_eq "$(grep -n -E '\\$' "$f" || true)" "" "$(basename "$f"): 줄 이음(\\) 없음"
    assert_eq "$(grep -n -E '(^|[^A-Za-z_])eval( |$)' "$f" || true)" "" "$(basename "$f"): eval 을 쓰지 않음 (NFR6.4)"
    assert_eq "$(grep -n -E '[^A-Za-z_"]docker' "$f" | grep -v '^[0-9]*:[[:space:]]*#' || true)" "" "$(basename "$f"): docker 명령 없음"
  done
  assert_eq "$(grep -c -E '^[a-z_][a-z0-9_]*\(\) \{$' "$SCRIPTS_DIR/run-local.sh" | tr -d ' ')" "$(grep -c -E '^[a-z][a-z0-9_]*\(\) \{$' "$SCRIPTS_DIR/run-local.sh" | tr -d ' ')" "함수 이름은 소문자 스네이크 케이스"
  assert_eq "$(grep -E '^[A-Za-z_]+\(\) \{$' "$SCRIPTS_DIR/run-local.sh" | grep -v -E '^[a-z][a-z0-9_]*\(\) \{$' || true)" "" "대문자가 섞인 함수 이름 없음"
}

case_untouched_files_and_proxy_rules() {
  new_light static-untouched
  assert_eq "$(grep -n 'gitignore' "$SCRIPTS_DIR/run-local.sh" "$SCRIPTS_DIR/smoke-local.sh" "$SCRIPTS_DIR/verify-all.sh" | grep -v 'LOCAL_RUN_DIR' | grep -v '^[^:]*:[0-9]*:[[:space:]]*#' || true)" "" "루트 .gitignore 를 건드리지 않음 (.local-run 자체 .gitignore 만 씀)"
  assert_eq "$(grep -n -E 'vite\.proxy|vite\.config' $PRODUCT_SCRIPTS || true)" "" "화면 프록시 규칙 파일을 읽거나 바꾸지 않음 (NFR6.6)"
  assert_eq "$(grep -n -E 'services/[a-z0-9-]+/(src|pom\.xml)' $PRODUCT_SCRIPTS | grep -v 'target/classes' || true)" "" "서비스 소스를 직접 건드리지 않음 (NFR4.1)"
}

case_no_hardcoded_secrets() {
  new_light static-secrets
  assert_eq "$(scan_secrets $ALL_SCRIPTS)" "" "스크립트와 시험에 하드코딩된 비밀 값 0건 (시험용 고정 가짜 값 제외)"
  # 검색 자체가 비어 있지 않은지: 하드코딩 비밀 값이 든 예제를 걸러 내는가
  # 걸러 낼 예제는 리터럴로 적지 않고 실행 중에 만든다 (이 파일 자체가 검색에 걸리지 않도록)
  local filler hex64
  filler="$(printf 'q%.0s' 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20)"
  hex64="$(printf 'ab%.0s' $(seq 1 32))"
  printf 'PAYMENT_WEBHOOK_SECRET=%s\n' "$filler" > "$WORK/bad1.sh"
  printf 'x=%s\n' "$hex64" > "$WORK/bad2.sh"
  printf 'PAYMENT_WEBHOOK_SECRET="$SECRET"\n' > "$WORK/good.sh"
  assert_true "대입된 비밀 값 패턴을 찾아냄" test -n "$(scan_secrets "$WORK/bad1.sh")"
  assert_true "64자 16진수 값을 찾아냄" test -n "$(scan_secrets "$WORK/bad2.sh")"
  assert_eq "$(scan_secrets "$WORK/good.sh")" "" "변수 참조는 걸리지 않음"
  assert_eq "$(grep -c 'test-only-fixed-fake' "$TEST_LIB_DIR/run-local.test.sh" | tr -d ' ')" "1" "시험용 고정 가짜 값은 시험 한 곳에만 있음"
}

case_shellcheck_when_available() {
  new_light static-shellcheck
  if command -v shellcheck > /dev/null 2>&1; then
    local f bad=""
    for f in $PRODUCT_SCRIPTS; do
      shellcheck -s bash -S error "$f" > /dev/null 2>&1 || bad="$bad $f"
    done
    assert_eq "$bad" "" "shellcheck 오류 없음"
  else
    echo "  shellcheck 가 없어 건너뜁니다 (이 머신에는 설치되어 있지 않음)"
    ok "shellcheck 없음: 건너뜀을 알림"
  fi
}

run_case case_bash_syntax_check
run_case case_scripts_run_on_bash_3_2
run_case case_no_bash4_features
run_case case_product_script_conventions
run_case case_untouched_files_and_proxy_rules
run_case case_no_hardcoded_secrets
run_case case_shellcheck_when_available

suite_end

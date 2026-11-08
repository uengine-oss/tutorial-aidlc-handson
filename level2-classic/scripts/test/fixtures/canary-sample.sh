#!/usr/bin/env bash
# xtrace 줄 번호 보존 카나리용 예제. 줄 번호를 시험이 직접 확인하므로 줄을 옮기지 않는다.
set -euo pipefail

work_func() {
  local a=1
  a=2
  return 0
}

on_exit() {
  printf 'exit-handler\n'
}

on_signal() {
  printf 'signal-handler\n'
}

trap on_exit EXIT
trap 'on_signal' HUP
work_func
value="$(printf 'sub')"
( printf 'subshell\n' )
case "$value" in
  sub)
    printf 'case-hit\n'
    ;;
  other)
    printf 'case-miss\n'
    ;;
esac
if [ "$value" = "sub" ]; then
  printf 'if-hit\n'
else
  printf 'else-miss\n'
fi
for item in one two; do
  printf 'loop %s\n' "$item"
done
sleep 1 &
kill -HUP "$$"
wait "$!" || true
/bin/bash "$(dirname "${BASH_SOURCE[0]}")/canary-child.sh"

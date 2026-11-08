#!/usr/bin/env bash
# 커버리지 측정기 시험용 예제. 줄 번호를 시험이 직접 확인하므로 줄을 옮기지 않는다.
set -euo pipefail

alpha() {
  local x="$1"
  if [ "$x" = "a" ]; then
    printf 'a\n'
  else
    printf 'not a\n'
  fi
}

beta() {
  case "$1" in
    one)
      printf '1\n'
      ;;
    *)
      printf 'other\n'
      ;;
  esac
}

gamma() {
  for i in 1 2; do
    printf '%s\n' "$i"
  done
}

long_command() {
  printf '%s %s\n' \
    "a" \
    "b"
}

unused() {
  printf 'never\n'
  printf 'never2\n'
}

alpha "${1:-a}"
beta "${2:-one}"
gamma
long_command
if [ "${3:-}" = "all" ]; then
  unused
fi

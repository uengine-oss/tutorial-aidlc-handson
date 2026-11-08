#!/usr/bin/env bash
# 카나리용 자식 스크립트. 줄 번호를 시험이 직접 확인하므로 줄을 옮기지 않는다.
set -euo pipefail
printf 'child-line\n'
( printf 'child-subshell\n' )

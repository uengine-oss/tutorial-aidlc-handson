#!/usr/bin/env bash
# VoiceParking 프로토타입 전체 기동: 백엔드 3개 + 프런트 개발 서버.
# 사용법: bash run-all.sh   (종료는 Ctrl+C)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PIDS=()

cleanup() {
  echo "서비스를 종료합니다..."
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
}
trap cleanup EXIT INT TERM

echo "백엔드를 빌드합니다 (테스트 생략)..."
mvn -q -f "$ROOT/backend/pom.xml" package -DskipTests

for svc in recommendation-service payment-service reservation-service; do
  echo "$svc 기동"
  java -jar "$ROOT/backend/$svc/target/$svc-0.1.0.jar" &
  PIDS+=("$!")
done

if [ ! -d "$ROOT/frontend/node_modules" ]; then
  npm --prefix "$ROOT/frontend" install
fi

echo "프런트 개발 서버: http://localhost:5173"
npm --prefix "$ROOT/frontend" run dev

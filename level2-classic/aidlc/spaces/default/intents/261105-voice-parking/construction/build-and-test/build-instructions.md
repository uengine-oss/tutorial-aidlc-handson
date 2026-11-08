# 빌드 안내

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] u1~u5의 code-generation-plan.md, unit-test-instructions.md, code-summary.md, team.md(Docker 금지, 별도 배포 환경 금지, TDD, 80% 하한), project.md(로컬 실행 스크립트 필수)

## 준비 도구

| 도구 | 버전 | 용도 |
|---|---|---|
| Java | 17 | u1~u3 (Spring Boot 3.5.7) |
| Maven | 3.9.x | u1~u3 빌드와 시험 |
| Node.js / npm | 25.x / 11.x | u4 (Vue 3, Vite, Vitest) |
| bash | 3.2 이상 (`/bin/bash`) | u5 스크립트와 시험 |
| curl, lsof, openssl | 기본 설치 | u5 스크립트 (`openssl`이 없으면 `od`로 대체) |

Docker는 쓰지 않는다. 별도 배포 환경도 없다.

## 의존성 설치와 첫 준비

저장소 루트에서 실행한다.

```bash
scripts/run-local.sh --prepare
```

`mvn -q -DskipTests compile`(u1~u3)과 `npm --prefix services/u4-web-ui install`을 실행한다. `--prepare` 없이 서비스를 띄우면 준비되지 않은 빌드(`target/classes` 또는 `node_modules` 없음)를 감지해 안내하고 거부한다.

## 빌드와 검증 명령

| 대상 | 명령 | 내용 |
|---|---|---|
| u1~u3 전체 | `mvn -q verify` | 컴파일, 시험, JaCoCo 라인 80% 하한 검사 |
| u2 실제 180초 시험 | `mvn -q -pl services/u2-reservation-service verify -Pslow` | 실제 마감 시험 포함, 약 190초 |
| u4 | `npm --prefix services/u4-web-ui run verify` | lint, test(unit → timing → proxy), 커버리지 80% 하한, build, audit 보고 |
| u5 | `/bin/bash scripts/test/run-all.sh` | 정적 검사, 시나리오 시험, 스모크·검증 스크립트 시험, 줄 커버리지 80% 하한 |
| 전체 | `scripts/verify-all.sh [--slow]` | 위 단계를 모두 실행하고 실패 단계를 요약 |

현재 승인된 Construction Verification Command는 `mvn -q verify && npm --prefix services/u4-web-ui run verify`이다.

## 환경 변수

실행 스크립트가 서비스별로 넘기는 변수는 `scripts/run-local.sh --help`가 보인다. 요약:

| 서비스 | 변수 |
|---|---|
| u1 | `SERVER_PORT`(8081), `SERVER_ADDRESS`(127.0.0.1) |
| u2 | + `SEARCH_SERVICE_URL` |
| u3 | + `RESERVATION_SERVICE_URL`, `WEBHOOK_URL`, `PAYMENT_WEBHOOK_SECRET`(없으면 실행마다 무작위 생성) |
| u4 | `U1_URL`, `U2_URL`, `U3_URL`, `UI_HOST`(127.0.0.1), 포트 5173 고정 |

## 빌드 확인

1. `mvn -q verify`가 종료 코드 0으로 끝난다.
2. `npm --prefix services/u4-web-ui run verify`가 종료 코드 0으로 끝난다.
3. `/bin/bash scripts/test/run-all.sh`가 종료 코드 0으로 끝난다.
4. `scripts/run-local.sh`가 u1 → u2 → u3 → u4 순서로 준비 확인을 통과하고 접속 주소를 안내한다.

## 문제 해결

| 증상 | 원인 | 조치 |
|---|---|---|
| 시간 시험(u3 성능, u4 틱)이 간헐 실패 | 호스트 부하가 높음(코어 수를 크게 넘는 부하 평균) | 목표는 낮추지 않는다. 부하가 낮을 때 다시 실행한다. `verify-all.sh`는 부하 평균 60 이상이면 경고한다 |
| 포트 사용 중 | 8081~8083, 5173을 다른 프로세스가 사용 | 사용 중인 포트를 알려 주면 해당 프로세스를 종료하거나 `U1_PORT` 등으로 바꾼다(5173은 고정) |
| 준비 시간 초과 | 첫 실행의 컴파일, 높은 부하 | `--prepare`를 먼저 실행한다. 로그는 `.local-run/logs/<서비스>.log` |
| `npm audit` 불가 | 레지스트리 접근 불가 | 보고 전용이라 빌드는 실패하지 않는다 |

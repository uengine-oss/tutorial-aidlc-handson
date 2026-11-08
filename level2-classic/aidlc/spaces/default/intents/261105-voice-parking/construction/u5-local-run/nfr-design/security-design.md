# u5-local-run 보안·실행 설계

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] nfr-requirements/security-requirements.md(NFR6.1~NFR6.6, 서비스별 주입 변수 표), tech-stack-decisions.md(NFR4.1, NFR7.1~NFR7.3, NFR8.2~NFR8.9, NFR9.1), 요구사항 검토에서 넘어온 항목, stories.md의 US5.1, u1~u4의 code-summary.md(실행 명령, 환경 변수, 상태 확인 경로, 루프백 기본값), contract-summary.md(포트)

이 유닛은 packaging 유닛이라 기능 설계 단계의 functional-spec이 없다(u5 functional-spec 없음, 동작은 stories.md의 US5.1과 요구사항 문서가 정한다). 서비스의 실행 계약은 u1~u4의 functional-spec.md(포트, 환경 변수, 상태 확인, 400 거절)를 따른다. 이 설계 문서가 보안과 실행 구조를 함께 다룬다. 성능·확장성·신뢰성·관측성 설계 문서는 만들지 않는다(요구사항 단계가 해당 파일을 만들지 않았다).

## 스크립트 구조

`scripts/run-local.sh`는 함수 단위로 나눈다. 시험과 커버리지 대응표가 함수 단위로 이루어진다.

| 함수 | 책임 | 요구사항 |
|---|---|---|
| `load_config` | 설정 구역(기본 포트 8081·8082·8083, 호스트 127.0.0.1, 준비 제한 90초, 폴링 1초, 감시 2초)을 읽고 환경 변수가 있으면 덮어쓴다 | NFR8.1, NFR8.6 |
| `validate_config` | 포트 형식·범위·중복, 비루프백 `SERVER_ADDRESS`·`UI_HOST`, `UI_ALLOW_NON_LOOPBACK` 거부, 사용자가 준 비밀 값 형식·길이 검사 | NFR6.3, NFR6.4 |
| `derive_urls` | 포트에서 서비스 주소(`SEARCH_SERVICE_URL`, `RESERVATION_SERVICE_URL`, `WEBHOOK_URL`, `U1_URL`~`U3_URL`)를 계산 | NFR8.1 |
| `make_secret` | 사용자 값이 없으면 `openssl rand -hex 32`, 실패하거나 64자 미만이면 `od` 대체, 그래도 64자 미만이면 거부 | NFR6.1 |
| `check_ports` | 네 포트(8081, 8082, 8083, 5173)가 비어 있는지 `lsof`로 확인 | NFR8.3 |
| `check_prepared` | `target/classes`와 `services/u4-web-ui/node_modules` 존재 확인, 없으면 `--prepare` 안내와 거부 | NFR8.5 |
| `start_service` | 서비스를 자기 프로세스 그룹에서 `</dev/null`로 띄우고 로그를 `.local-run/logs/`에 쓴다. 환경은 주입 변수 표대로만 구성 | NFR6.2, NFR8.2 |
| `wait_ready` | 준비 확인 규칙대로 폴링, 제한 초과·프로세스 종료를 구분해 보고 | NFR8.3, NFR8.9 |
| `watch_services` | 기동 뒤 2초 간격으로 생존 확인, 종료되면 알리고 정리 | NFR8.8 |
| `cleanup` | 중복 실행 방지 플래그, 그룹 SIGTERM → 10초 유예 → SIGKILL → 포트 닫힘 확인 | NFR8.4 |
| `prepare` | `mvn -q -DskipTests compile`(u1~u3), `npm --prefix services/u4-web-ui install` | NFR8.5 |
| `usage` | `--help` | NFR8.6 |
| `main` | 인자 처리와 순서(`u1 → u2 → u3 → u4`) | NFR8.2 |

## 설계 결정

| 설계 | 방식 | 충족하는 요구사항 |
|---|---|---|
| 환경 구성 | 서비스마다 `env -u PAYMENT_WEBHOOK_SECRET -u UI_ALLOW_NON_LOOPBACK`로 시작하는 환경에 주입 변수 표의 변수만 더한다. u3만 `PAYMENT_WEBHOOK_SECRET`을 받는다. 사용자가 셸에 둔 같은 이름 변수가 다른 서비스에 상속되지 않는다 | NFR6.2, NFR6.3 |
| 비밀 값 | 생성 값은 64자 이상 16진수, 사용자 값은 32자 이상·공백·제어 문자·따옴표 없음. 값은 프로세스 인자에 넣지 않고 환경 변수로만 u3에 준다. 화면에는 "비밀 값: 환경 변수 값 사용" 또는 "무작위 값 생성"만 알리고 값은 출력하지 않는다. 생성한 값은 파일에 저장하지 않는다 | NFR6.1, NFR6.2 |
| 입력 검증 | 포트는 `case`로 숫자만 허용하고 1024~65535를 비교한다. 설정값은 항상 따옴표로 확장하며 `eval`을 쓰지 않는다 | NFR6.4 |
| 프로세스 그룹과 정리 | `set -m`으로 서비스마다 그룹을 만든다. 서비스는 `</dev/null`로 시작해 터미널 입력으로 멈추지 않는다. `cleanup`은 한 번만 실행(플래그)하고 INT·TERM·HUP·EXIT trap이 호출한다. 정리 중 두 번째 신호는 무시한다(`trap '' INT TERM HUP`). 이미 끝난 그룹에 대한 `kill`·`wait` 실패는 허용한다(`|| true`). 종료 메시지(`Terminated` 등)는 `wait` 출력을 무시한다. 마지막에 네 포트가 닫혔는지 확인해 열려 있으면 경고한다 | NFR8.4 |
| 준비 확인 | 폴링 간격 1초, `curl --silent --max-time 2`. u1~u3은 `/actuator/health` 본문에 `"status":"UP"`이 있을 때 준비. u4는 `/`가 200이고, 프록시를 통과해 u1에 닿았음을 보는 점검을 쓴다: u4(5173)로 `POST /api/search/recommendations`(헤더 `Content-Type: application/json`, 세션 UUID 헤더, 본문 `{}`)를 보내 상태 코드 400을 받으면 준비(u1이 빈 입력을 400으로 거절하는 계약, BR1.5). 400이 아닌 응답(5xx 전체, 404, 연결 거부)은 준비가 아니다. 준비 제한 90초는 서비스당이다 | NFR8.9, NFR8.5 |
| 기동 뒤 감시 | `watch_services`는 2초마다 각 그룹의 생존을 보고 하나라도 종료되면 어느 서비스인지 알리고 `cleanup` 후 1로 종료한다 | NFR8.8 |
| 로그 | `umask 077` 뒤 `.local-run/logs/`를 만들고 서비스별 로그를 `>`로 덮어쓴다. `.local-run/.gitignore`(내용 `*`)를 만든다 | NFR6.5 |
| 프록시 규칙 비간섭 | 스크립트는 화면에 `U1_URL` 등 주소만 주며 `vite.proxy.js`를 읽거나 바꾸지 않는다 | NFR6.6 |
| 연동 스모크 | `scripts/smoke-local.sh`가 5173을 통해 시나리오를 실행하고 `.local-run/smoke.txt`에 단계별 결과와 시간을 남긴다. 웹훅 미도달 판정: 프록시 경유 `POST /api/payments/webhook`과 `POST /internal/reservations`의 응답이 404(또는 405)이고, 호출 전후 u3 로그의 웹훅 경로 수신 줄 수가 같다(스모크는 결제 요청 전에 이 점검을 하므로 모의 PG의 정상 호출과 섞이지 않는다) | NFR8.7, NFR6.6 |
| 전체 검증 | `scripts/verify-all.sh [--slow]`는 단계(`mvn -q verify [-Pslow]`, `npm run verify`, u5 시험·커버리지)를 순서대로 모두 실행하고 실패한 단계를 요약한다. 시작 때 부하 평균을 출력하고 60 이상이면 경고한다. 간헐 실패를 가리지 않는다(재실행하지 않는다) | NFR7.3 |

## 시험과 커버리지 측정 설계

- 시험 하네스(`scripts/test/run-local.test.sh`)는 `/bin/bash`(3.2)로 실행되고 시나리오마다 가짜 서비스를 만든다. 가짜 서비스는 셸 래퍼가 Node 서버를 자식·손자로 띄우는 형태이며 자기 환경과 인자를 파일에 남긴다. 비밀 값 상속 차단, 주입 변수 도출, 루프백 값을 이 기록으로 확인한다.
- 시나리오: 기동 성공, 포트 사용 중, 서비스 즉시 종료, 준비 시간 초과(제한을 환경 변수로 줄여 시험), 준비되지 않은 빌드, 준비 판정(u4 400/500/연결 거부), 중단 정리(자식·손자), SIGTERM 무시(SIGKILL), 기동 뒤 종료 감시, 두 번째 Ctrl-C, 잘못된 설정값·같은 포트 둘·비루프백·`UI_ALLOW_NON_LOOPBACK`, 비밀 값(생성 길이 64 이상, 사용자 값 짧음 거부, `openssl` 실패 대체), U3 포트 변경이 웹훅 주소에 반영, 로그 권한과 덮어쓰기, `--help`, `--prepare`, 스모크 스크립트 자체 시험, `verify-all.sh` 단계 조합.
- **커버리지 측정(bash 3.2에서 동작)**: `BASH_XTRACEFD`를 쓰지 않는다. 하네스가 스크립트를 `SHELLOPTS=xtrace`와 `PS4='+TRACE|${BASH_SOURCE}|${LINENO}|'`을 export한 환경에서 실행하고(`SHELLOPTS`는 자식 bash와 서브셸에 상속된다) 표준 오류를 시나리오별 파일에 받는다. 스크립트 자신의 표준 오류 메시지는 `+TRACE|`로 시작하지 않으므로 파싱에서 걸러진다. 모든 시나리오 파일을 합쳐 `run-local.sh`의 줄 번호별 실행 여부를 모은다. 분모는 실행 가능한 줄이다: 빈 줄, 주석 줄, `fi`·`done`·`esac`·`}`·`;;`·`else`·`then`·`do`만 있는 줄을 제외하고, 스크립트에 here-doc을 쓰지 않아(`printf` 사용) 본문 줄이 섞이지 않게 한다. `set -e` 때문에 중간에 끝나는 경로는 그 시나리오가 지난 줄까지만 센다. 합친 실행 비율이 80% 미만이면 `scripts/test/coverage.sh`가 실패한다.
- 서브프로세스(백그라운드 작업, `trap` 처리기, 서브셸)의 xtrace도 같은 stderr 파일 설명자를 상속하므로 시나리오 파일에 함께 기록된다.

## Assumptions & Open Questions

- [assumption] u1은 `{}` 본문을 400으로 거절한다(INVALID_TEXT). 실제 서비스에서 이 점검이 통과하는지는 연동 스모크가 처음 확인한다. 응답이 400이 아니면 준비로 보지 않으므로 서비스가 정상이어도 실패할 수 있다. 그때는 이 규칙을 u1의 실제 공개 계약에 맞춰 바로잡는다.
- [assumption] Vite가 대상 연결 실패를 502 또는 500으로 돌려줄 수 있다. 어느 쪽이든 400이 아니므로 준비 판정이 같다.
- [assumption] `SHELLOPTS`로 xtrace를 상속시키는 방식이 bash 3.2의 서브셸·자식 bash에서 줄 번호를 보존한다. 구현할 때 첫 시험으로 확인하고, 안 되면 xtrace 수집 방식을 바꾼다.
- 열린 질문: 없음.

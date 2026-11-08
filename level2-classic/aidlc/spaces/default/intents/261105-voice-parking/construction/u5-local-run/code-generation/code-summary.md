# u5-local-run 코드 요약

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] code-generation-plan.md(Step 1~20, 결정 G1~G10), unit-test-instructions.md, nfr-design/security-design.md, nfr-requirements/security-requirements.md와 tech-stack-decisions.md, contract-summary.md(C1, 포트), u1~u4의 code-summary.md, team.md(TDD, 80% 하한, Docker 금지, 별도 배포 환경 금지), project.md(로컬 실행 스크립트 필수)

## 만든 것

Docker 없이 u1~u4를 한 번에 띄우고 정리하는 로컬 실행 스크립트와 그 시험이다. 저장소 루트의 새 `scripts/` 폴더에만 만들었고 루트 `.gitignore`, `pom.xml`, `services/**`는 건드리지 않았다. 런타임 산출물(로그, 스모크 결과)은 `.local-run/`에 쓰며 그 폴더가 자체 `.gitignore`(내용 `*`)를 만든다.

| 파일 | 내용 |
|---|---|
| `scripts/run-local.sh` | 실행, `--prepare`, `--help`. 함수 단위(`load_config`, `validate_config`, `derive_urls`, `make_secret`, `check_ports`, `check_prepared`, `setup_logs`, `start_service`, `wait_ready`, `watch_services`, `cleanup`, `prepare`, `usage`, `main`과 도우미). 설정 구역이 맨 위 |
| `scripts/smoke-local.sh` | 연동 스모크(화면 프록시 5173 경유 추천 -> 결제 -> `CONFIRMED` 폴링, 웹훅·내부 경로 미도달 확인). 결과는 `.local-run/smoke.txt` |
| `scripts/verify-all.sh` | 병합 전 검증 `[--slow]`: `mvn -q verify [-Pslow]`, `npm --prefix services/u4-web-ui run verify`, u5 시험과 커버리지. 실패해도 모든 단계를 실행하고 요약, 재실행 없음, 시작 때 부하 평균 출력(60 이상 경고) |
| `scripts/test/lib.sh` | 시험 도우미(확인 함수, 임시 작업 폴더, 가짜 서비스 기동, run-local.sh 실행) |
| `scripts/test/fakes/fake-service.sh`, `fake-server.js` | 가짜 mvn/npm/docker 래퍼(자식·손자 프로세스, 환경·인자 기록, 동작 모드)와 Node 가짜 서버 |
| `scripts/test/run-local.test.sh` | run-local.sh 시나리오 시험 |
| `scripts/test/smoke-local.test.sh`, `verify-all.test.sh` | 스모크, 전체 검증 스크립트 자체 시험 |
| `scripts/test/coverage.sh` | xtrace 줄 커버리지 측정기와 80% 하한 검사 |
| `scripts/test/coverage.test.sh` | 측정기 자체 시험과 bash 3.2 xtrace 카나리(G1) |
| `scripts/test/static.test.sh` | `bash -n`, bash 3.2/4 기능, 스타일 규칙, 하드코딩된 비밀 값 검색, `shellcheck`(있으면) |
| `scripts/test/run-all.sh` | 위 시험을 `/bin/bash`로 차례로 실행하고 요약 |
| `scripts/test/fixtures/` | `canary-sample.sh`, `canary-child.sh`, `cov-sample.sh` (카나리와 측정기 시험용 예제) |

계획의 코드 배치에 없던 파일: `static.test.sh`(Step 19의 정적 검사·비밀 값 검색), `coverage.test.sh`(Step 18의 측정기 시험), `fixtures/`.

## 실행 방법

저장소 루트에서:

```
scripts/run-local.sh --prepare     # 첫 실행 전 한 번: mvn -q -DskipTests compile, npm --prefix services/u4-web-ui install
scripts/run-local.sh               # u1 -> u2 -> u3 -> u4 기동, 접속 주소 안내, 포그라운드 대기, Ctrl-C 로 정리
scripts/run-local.sh --help        # 환경 변수 목록과 기본값
scripts/smoke-local.sh             # 서비스가 떠 있을 때 연동 확인 (.local-run/smoke.txt)
scripts/verify-all.sh [--slow]     # 병합 전 검증
/bin/bash scripts/test/run-all.sh  # u5 전체 시험
```

시험 명령(단위 범위): `/bin/bash scripts/test/run-local.test.sh`, `smoke-local.test.sh`, `verify-all.test.sh`. 커버리지: `/bin/bash scripts/test/coverage.sh` (단독 실행, 하한 미달일 때만 0이 아닌 종료 코드). 반복 개발용으로 `RUN_ONLY=<정규식>`으로 시험을 골라 돌릴 수 있고 `T_WAIT`(기다림 상한, 기본 120초)를 환경 변수로 바꾼다.

서비스는 각 유닛의 공개 실행 명령만 쓴다: `mvn -q -pl services/<서비스> spring-boot:run`(u1~u3), `npm --prefix services/u4-web-ui run dev`(u4, 화면 포트 5173 고정).

## 환경 변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `U1_PORT`, `U2_PORT`, `U3_PORT` | 8081, 8082, 8083 | 1024~65535 정수, 서로 달라야 하고 5173과도 달라야 함. 바꾸면 서비스 주소가 함께 바뀜 |
| `SERVER_ADDRESS`, `UI_HOST` | 127.0.0.1 | 루프백(127.0.0.1, localhost, ::1)만 허용. 서비스에는 항상 127.0.0.1을 넘김 |
| `UI_ALLOW_NON_LOOPBACK` | (없음) | 설정되어 있으면 값과 관계없이 실행 거부 |
| `PAYMENT_WEBHOOK_SECRET` | (실행마다 무작위 64자 16진수) | 주면 32자 이상, 공백·제어 문자·따옴표 없음. u3에만 넘김. 화면·로그·인자에 값을 쓰지 않고 파일에 저장하지 않음 |
| `RUN_LOCAL_READY_TIMEOUT` | 90 | 서비스당 준비 확인 제한(초, 정수) |
| `RUN_LOCAL_POLL_INTERVAL` | 1 | 준비 확인 폴링 간격(초) |
| `RUN_LOCAL_WATCH_INTERVAL` | 2 | 기동 뒤 생존 감시 간격(초) |
| `RUN_LOCAL_TERM_GRACE` | 10 | SIGTERM 뒤 SIGKILL까지의 유예(초, 정수) |
| `RUN_LOCAL_ROOT`, `RUN_LOCAL_UI_PORT` | (스크립트 위 폴더), 5173 | 시험용. 가짜 서비스 폴더와 비어 있는 화면 포트를 주려고 둠. 실제 u4는 5173 고정이라 `RUN_LOCAL_UI_PORT`를 바꾸면 안 됨 |
| `SMOKE_BASE_URL`, `SMOKE_CONFIRM_TIMEOUT`, `SMOKE_POLL_INTERVAL`, `SMOKE_LOG_DIR` | http://127.0.0.1:5173, 5, 0.5, `.local-run/logs` | smoke-local.sh. 확정 대기 기본 5초는 완화하지 않음 |
| `VERIFY_U5_SCRIPT`, `VERIFY_LOAD_AVG` | `scripts/test/run-all.sh`, (uptime) | verify-all.sh 시험용 |

서비스에 넘기는 환경은 `security-requirements.md`의 주입 변수 표와 같다. 서비스를 띄울 때 `env -u`로 표의 이름 전체와 `UI_ALLOW_NON_LOOPBACK`, `SHELLOPTS`, `PS4`를 먼저 지우고 그 서비스의 변수만 다시 준다(G5). 비밀 값은 서브셸 안의 `export`로 u3에만 준다(명령줄 인자에 값이 없음).

## 커버리지 측정 (G1, G8)

- 대상은 `scripts/run-local.sh`. 모든 시나리오를 `COVERAGE=1`로 돌려 `SHELLOPTS=xtrace`와 `PS4='+TRACE|${BASH_SOURCE:-}|${LINENO:-0}|'`로 실행된 줄을 모으고 분모와 비교한다. 하한 80%는 `coverage.sh`의 상수이며 인자나 환경 변수로 낮출 수 없다.
- **분모 제외 규칙(`scripts/test/coverage.sh`의 `denominator` 한곳)**: 빈 줄, 주석 줄, `fi`·`done`·`esac`·`}`·`;;`·`else`·`then`·`do`만 있는 줄, 함수 머리줄(`name() {`), `case` 라벨 줄(`pattern)`), `\`로 이어진 뒤쪽 줄. 계획이 정한 목록 그대로이며 규칙을 더하지 않았다(비율을 올리려고 규칙을 더하려면 사람의 승인이 필요).
- 스크립트 작성 규칙: 한 명령은 한 줄, `\` 이어쓰기와 here-doc 금지(정적 시험이 확인). `run-local.sh`는 here-doc 대신 `printf`를 쓴다.
- 근사 측정이다: 한 줄 안의 조건 분기(`a || b`, `if ...; then x; fi` 한 줄)는 세지 못하고 한 번 지나가면 실행된 것으로 센다. 비율, 실행되지 않은 줄 번호, 분모 줄 수를 출력한다.
- **bash 3.2 xtrace 카나리 결과(Step 2)**: `SHELLOPTS=xtrace`와 `PS4`는 함수, 서브셸, 명령 치환, 자식 bash 스크립트, `trap` 처리기 안에서 원래 줄 번호를 보존한다(`coverage.test.sh`가 확인). 단 **bash 3.2는 PS4 확장 결과를 99자에서 자른다.** 스크립트를 긴 절대 경로로 부르면 `BASH_SOURCE`가 길어져 줄 번호가 사라진다(이 저장소의 절대 경로는 100자 안팎). 그래서 시험은 작업 폴더에서 `../../../scripts/run-local.sh` 같은 짧은 상대 경로로 `run-local.sh`를 부르고, `coverage.sh`는 잘린 기록(`+TRACE|` 뒤에 `|줄|`이 없는 줄)이 하나라도 있으면 조용히 넘기지 않고 실패로 알린다. 방식(xtrace 줄 번호) 자체는 바꾸지 않았다.
- `PS4`에 `${BASH_SOURCE:-}`, `${LINENO:-0}`를 쓴다. `set -u` 아래의 `eval`/`-c` 문자열에는 `BASH_SOURCE`가 없어 기본값 없는 `${BASH_SOURCE}`는 셸을 죽인다.

## 상류 문서를 대체한 결정 (G3)

- `security-design.md`와 계획의 G3가 `tech-stack-decisions.md`의 NFR8.9("502가 아닌 응답"), 준비 확인 행, 커버리지 측정 행(`BASH_XTRACEFD`)을 대체한다. u4 준비 판정은 `/`가 200이고 5173으로 보낸 `POST /api/search/recommendations`(`Content-Type: application/json`, `X-Session-Id: <UUID>`, 본문 `{}`)가 정확히 400일 때만 준비다. 500, 502, 404, 200, 연결 거부는 준비가 아니다. UUID는 `uuidgen`, 없으면 `od`로 만든 16진수로 조립한다.
- **공유 계약 C1의 400 확인 결과**: `contract-summary.md`의 C1은 200과 422만 적고 400이 없다. u1 코드(`SearchController`, `ErrorCode`)와 u1 code-summary.md는 빈 `text`에 400 `INVALID_TEXT`, 세션 헤더가 없거나 UUID가 아니면 400 `MISSING_SESSION`을 돌려준다고 확인한다(functional-spec BR1.5, BR1.6). 즉 이 점검은 공개 계약이 아니라 u1의 구현된 동작에 기댄다. 계약 문서가 갱신될 때까지 한계로 둔다. 실제 서비스에서 통과하는지는 연동 스모크와 Build and Test가 처음 확인한다.
- 비밀 값 길이(G6): u3의 `HmacSigner`는 비어 있지 않기만 요구한다. 생성 값은 64자 이상, 사용자 값은 32자 이상으로 두었다.

## 시험 결과

`/bin/bash scripts/test/run-all.sh`(macOS `/bin/bash` 3.2.57, 전체 통과, 종료 코드 0)의 마지막 실행 결과:

| 단계 | 시험(case) | 확인(assertion) | 실패 |
|---|---|---|---|
| 정적 검사(`static.test.sh`) | 7 | 39 | 0 |
| run-local.sh 시나리오(`run-local.test.sh`) | 42 | 253 | 0 |
| smoke-local.sh 시험 | 11 | 35 | 0 |
| verify-all.sh 시험 | 10 | 31 | 0 |
| 커버리지 측정기 자체 시험(`coverage.test.sh`, 카나리 포함) | 9 | 81 | 0 |
| 합계 | 79 | 439 | 0 |

줄 커버리지(`coverage.sh`): **100.0% (292 of 292 명령 줄)**, 하한 80% 통과, 실행되지 않은 줄 없음, 잘린 xtrace 기록 0줄. 이 수치는 근사 측정이다(한 줄 안의 분기는 세지 못함). 같은 xtrace 모드 시나리오 실행(42 case, 253 assertion)도 실패 0이었다.

### TDD 기록 (Red 먼저)

각 단계에서 시험을 먼저 쓰고 구현이 없는 상태에서 실패를 확인한 뒤 구현했다(첫 Green 실행 결과 포함).

| 단계 | Red(첫 실행) | Green(구현 뒤) |
|---|---|---|
| Step 3 설정·검증·비밀 값 | 13 case, 29 통과/43 실패 (뼈대는 종료 코드 70) | 13 case, 72 통과/0 실패 |
| Step 6 환경 격리·로그 | 21 case, 99 통과/27 실패 | 21 case, 126 통과/0 실패 |
| Step 9 기동 흐름 | 34 case, 145 통과/54 실패 | 34 case, 199 통과/0 실패 |
| Step 12 종료·감시 (8 case 범위) | 16 통과/29 실패 | 8 case, 45 통과/0 실패 |
| Step 15 스모크·전체 검증 | smoke 11 case 6 통과/29 실패, verify 10 case 6 통과/25 실패 | smoke 35 통과/0 실패, verify 31 통과/0 실패 (smoke 는 `json_field` 의 `set -e` 오류 하나를 고친 뒤) |
| Step 18 커버리지 측정기 | 9 case, 46 통과/35 실패 (`coverage.sh` 없음) | 81 통과/0 실패 |
| Step 19 정적 검사 | 첫 실행 1건 실패: 시험 안의 가짜 비밀 값 리터럴 두 곳이 패턴 검색에 걸림 | 리터럴을 실행 중 조립으로 바꾼 뒤 39 통과/0 실패 |

"거부해야 한다" 시험의 종료 코드 확인은 뼈대(종료 코드 70)에서도 통과하므로 Red 에서 통과 수에 포함된다. 메시지·동작 확인이 실패로 Red 를 만든다.

## 실행하지 않은 시험

- 실제 `mvn`/`npm`과 실제 서비스를 띄운 확인은 하지 않았다(계획과 지시: 시험은 가짜 서비스로만, 실제 서비스를 띄운 연동 확인은 Build and Test의 몫). `scripts/run-local.sh`, `smoke-local.sh`, `verify-all.sh`를 실제 u1~u4에 붙여 돌린 증거는 없다. u4 준비 판정(400 점검), 서비스별 준비 시간, 실제 JVM·node의 그룹 종료는 Build and Test에서 확인해야 한다.
- `shellcheck`와 `bats`는 이 머신에 없다. 정적 시험은 `shellcheck`가 있으면 돌고 없으면 건너뛴다고 알린다(이번 실행은 건너뜀).
- 비밀 값 비출력 확인은 일반 실행에서만 한다(G2). xtrace 실행(커버리지)은 이 확인을 건너뛰고 고정 가짜 비밀 값을 쓴다. 이 실행의 xtrace 기록에는 생성된 무작위 비밀 값이 남을 수 있어 `.local-run/coverage-work/`(기본은 끝나면 삭제, 소유자만 읽기)에만 둔다.

## 계획과 달라진 점, 한계

- 시험용 설정을 두었다: `RUN_LOCAL_ROOT`(가짜 서비스 폴더), `RUN_LOCAL_UI_PORT`(비어 있는 화면 포트), `SMOKE_*`, `VERIFY_U5_SCRIPT`, `VERIFY_LOAD_AVG`. 서비스에는 넘기지 않는다.
- `verify-all.sh`가 `run-all.sh`를 부르고 `run-all.sh`의 `verify-all.test.sh`가 `verify-all.sh`를 시험하므로, 시험은 `VERIFY_U5_SCRIPT`로 가짜 u5 스크립트를 주어 서로를 재귀로 부르지 않게 했다.
- 서비스 로그가 어디서 막히는지 보려고 기동 실패와 서비스 종료 때 그 서비스 로그의 마지막 20줄을 보인다(NFR8.3). 기동 뒤 서비스 하나가 끝났을 때(NFR8.8)도 같은 20줄을 보인다.
- `cleanup` 중 셸의 `Terminated` 작업 알림이 사용자에게 보이지 않도록 대기(`wait`)를 `2>/dev/null` 아래 둔 `pause`로 했다.
- 종료 코드: 설정 오류·포트 사용 중·준비되지 않은 빌드는 2, 기동 실패·서비스 종료는 1, 신호는 130(INT)·143(TERM)·129(HUP).
- 시험 한계: 부하 평균이 높은 이 머신에서 각 시험의 기다림 상한은 넉넉하게(`T_WAIT`, 120초) 두었다. 정의된 제품 목표(준비 90초, 유예 10초, 감시 2초, 스모크 확정 5초)는 설정 구역의 기본값 그대로이며 시험은 환경 변수로 줄여서 쓴다.
- `.gitignore`에 `.local-run/`을 넣지 않았다(루트 `.gitignore`는 사용자의 파일). `.local-run/`이 자체 `.gitignore`를 만든다.

## Construction Verification Command 전환 제안 (G9)

지금 승인된 명령은 `mvn -q verify && npm --prefix services/u4-web-ui run verify`다. Unit 체크포인트에서 사람이 아래로 바꾸는 것을 승인할지 정한다(이 계획은 승인된 명령을 바꾸지 않았다):

```
scripts/verify-all.sh
```

`--slow`를 붙이면 u2의 실제 180초 취소 시험까지 포함한다. u3·u4의 시간 시험이 호스트 부하 때문에 간헐 실패하는 문제(앞 유닛 code-summary)는 `verify-all.sh`가 재실행으로 가리지 않는다. 시작 때 부하 평균을 출력하고 60 이상이면 경고만 한다.

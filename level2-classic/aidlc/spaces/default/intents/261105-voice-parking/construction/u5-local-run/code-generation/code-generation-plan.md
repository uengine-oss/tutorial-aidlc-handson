# u5-local-run 코드 생성 계획

## Summary

- Builds: u1~u4를 Docker 없이 한 번에 띄우고 정리하는 로컬 실행 스크립트(`run-local.sh`), 연동 스모크(`smoke-local.sh`), 전체 검증(`verify-all.sh`), 이를 시험하는 bash 시험과 줄 커버리지 측정기
- Touches: 저장소 루트의 새 `scripts/` 폴더(실행 스크립트, `scripts/test/` 시험·가짜 서비스·커버리지). 루트 `.gitignore`와 기존 서비스 코드는 바꾸지 않는다
- Tests: `/bin/bash` 3.2로 도는 시나리오 시험 약 40개를 TDD(실패하는 시험 먼저)로 쓰고, xtrace 기반 줄 커버리지 80% 하한을 건다

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] nfr-requirements/(security-requirements.md의 주입 변수 표와 NFR6.1~NFR6.6, tech-stack-decisions.md의 NFR4.1, NFR7.1~NFR7.3, NFR8.2~NFR8.9, NFR9.1), nfr-design/security-design.md(함수 구조, 설계 결정, 시험·커버리지 설계), unit-of-work.md(U5), requirements.md, stories.md의 US5.1(AC5.1.1~AC5.1.3), contract-summary.md(포트, 웹훅 서명), u1~u4의 code-summary.md(실행 명령, 환경 변수, 앞 유닛이 u5에 넘긴 사항), team.md(TDD, 80% 하한, Docker 금지, 별도 배포 환경 금지), project.md(로컬 실행 스크립트 필수)

이 유닛은 packaging 유닛이라 기능 설계 단계의 functional-spec이 없다. 동작은 US5.1과 위 NFR 문서가 정한다.

## 설계 검토에서 넘어온 결정

설계 단계 검토(READY)가 남긴 항목은 설계 파일이 이미 검토를 마쳐 고칠 수 없으므로 이 계획이 구현 결정으로 닫는다. 아래 결정은 설계 문서와 어긋나는 곳에서 이 계획이 우선한다.

| # | 항목 | 결정 |
|---|---|---|
| G1 | 커버리지 분모 규칙 (설계 검토 Major) | 분모는 "명령이 있는 줄"이다. 제외: 빈 줄, 주석 줄, `fi`·`done`·`esac`·`}`·`;;`·`else`·`then`·`do`만 있는 줄, 함수 머리줄(`name() {`), `case` 라벨 줄(`pattern)`), `\`로 이어진 뒤쪽 줄. 스크립트 작성 규칙으로 분모를 안정시킨다: 한 명령은 한 줄, `\` 이어쓰기와 here-doc 금지, `&&`·`\|\|` 연결은 한 줄에. 이 제외 규칙은 `scripts/test/coverage.sh` 한곳에만 두고 `code-summary.md`에 목록을 적는다. 하한 80%는 낮추지 않고, 제외 규칙을 더해서 비율을 올리지 않는다(규칙 추가는 사람의 승인이 필요하다). 첫 시험(Step 2의 카나리)이 bash 3.2에서 `SHELLOPTS=xtrace`와 `PS4`로 줄 번호가 보존됨을 확인하고, 서브셸·자식 bash에서도 같은 줄이 찍히는지 함께 확인한다. 보존되지 않으면 수집 방식을 정할 때까지 멈추고 사람에게 알린다 |
| G2 | 비밀 값 시험과 xtrace 충돌 (설계 검토 Major) | 시험을 두 번 돌린다. 일반 실행(`run-local.test.sh`)은 xtrace 없이 모든 시나리오의 모든 확인을 한다. 비밀 값 출력·로그·인자 확인은 이 실행에서만 한다. 커버리지 실행(`coverage.sh`)은 같은 시나리오를 `SHELLOPTS=xtrace`로 돌리되 비밀 값 비출력 확인을 건너뛰고(`COVERAGE=1`), 시험용 고정 가짜 비밀 값을 쓴다. `start_service`는 서비스를 `env -u SHELLOPTS -u PS4`로 띄워 서비스 로그와 서브프로세스에 xtrace가 새지 않게 한다. 서브셸·trap 처리기 수집은 `run-local.sh` 자신의 서브셸만 대상이다(서비스 프로세스는 제외) |
| G3 | 상류 문서와 설계 불일치 (설계 검토 Minor) | `security-design.md`와 이 계획이 `tech-stack-decisions.md`의 NFR8.9 문구("502가 아닌 응답"), 준비 확인 행, 커버리지 측정 행(`BASH_XTRACEFD`)을 대체한다. 구현은 이 계획을 따른다. u4 준비 판정은 5173으로 `POST /api/search/recommendations`(헤더 `Content-Type: application/json`, `X-Session-Id: <UUID>`, 본문 `{}`)를 보내 상태 코드가 정확히 400일 때만 준비다. UUID는 `uuidgen`, 없으면 `od`로 만든 16진수에서 UUID 형식을 조립한다. u1이 400을 돌려주는 근거는 u1 functional-spec(BR1.5 INVALID_TEXT, BR1.6 MISSING_SESSION)이다. 공유 계약 C1에 400이 없으면 `code-summary.md`에 한계로 적는다 |
| G4 | 스모크 웹훅 미도달 판정 (설계 검토 Minor) | 판정은 상태 코드가 주다: 프록시 경유 `POST /api/payments/webhook`과 `POST /internal/reservations` 응답이 404 또는 405여야 통과한다. 로그 비교는 보조다: 웹훅 경로는 u3 로그, `/internal/reservations`는 u2 로그에서 호출 전후 수신 줄 수를 비교한다. 서비스가 요청 줄을 남기지 않으면 보조 비교는 "참고(로그 미지원)"로 표시하고 판정에 쓰지 않는다. 시험: 가짜 서비스가 200을 돌려주면 로그가 같아도 실패하고, 상태 404여도 가짜 로그 줄 수가 늘면 실패한다 |
| G5 | 환경 구성 (설계 검토 Minor) | 서비스를 띄울 때 `env -u`로 주입 변수 표의 이름 전체(`SERVER_PORT`, `SERVER_ADDRESS`, `SEARCH_SERVICE_URL`, `RESERVATION_SERVICE_URL`, `WEBHOOK_URL`, `PAYMENT_WEBHOOK_SECRET`, `U1_URL`, `U2_URL`, `U3_URL`, `UI_HOST`)와 `UI_ALLOW_NON_LOOPBACK`, `SHELLOPTS`, `PS4`를 먼저 지우고 그 서비스의 표 변수만 다시 설정한다. 사용자 셸의 같은 이름 값이 표에 없는 서비스에 상속되지 않는다. 비밀 값은 `env NAME=value`의 인자로 넘기지 않고 서브셸 안의 `export`로 u3에만 준다(명령줄 인자에 값이 없다) |
| G6 | 비밀 값 길이 | 생성 값은 64자 이상, 사용자 값은 32자 이상(HMAC-SHA256 키 256비트 이상 근거). 구현할 때 u3의 최소 길이 요구를 확인하고 u3가 더 길게 요구하면 u3 값을 따른다 |
| G7 | 프로세스 그룹과 정리 | `set -m` 아래에서 백그라운드로 시작한 작업의 `$!`가 그 그룹의 ID다(시험이 `ps -o pgid=`로 확인). `cleanup`은 한 번만 실행하고, 시작부터 끝(SIGKILL 대기와 포트 확인 포함)까지 INT·TERM·HUP를 무시한다. 두 번째 Ctrl-C 시험의 합격 기준: 정리가 중단되지 않고 한 번만 끝나며 남는 프로세스·열린 포트가 없다 |
| G8 | 측정 기록과 한계 | `scripts/test/coverage.sh`가 비율, 실행되지 않은 줄 번호, 분모 줄 수를 출력한다. 한 줄 안의 조건 분기는 세지 못하는 근사 측정이라는 한계를 `code-summary.md`에 적는다 |
| G9 | 통합 검증 명령 | `scripts/verify-all.sh [--slow]`는 `mvn -q verify [-Pslow]`, `npm --prefix services/u4-web-ui run verify`, u5 시험과 커버리지 순서로 모두 실행하고 실패한 단계를 요약한다. 프로젝트의 Construction Verification Command를 이 스크립트로 바꾸는 일은 Unit 체크포인트에서 사람이 승인하는 명령으로 제안한다. 이 계획은 승인된 명령을 바꾸지 않는다 |
| G10 | 하한과 시간 시험 | u3·u4의 부하 때문에 간헐 실패하는 시간 시험의 재확인은 `verify-all.sh` 안에서 재실행으로 가리지 않는다. 시작 때 부하 평균을 출력하고 60 이상이면 경고만 한다 |

## 코드 배치

저장소 루트의 `scripts/`에 둔다. 이 유닛의 `source-manifest.json`에 만든 파일을 모두 적는다. 루트 `.gitignore`와 `pom.xml`, `services/**`는 바꾸지 않는다.

```
scripts/run-local.sh                 실행, --prepare, --help (함수 단위, 설정 구역이 맨 위)
scripts/smoke-local.sh               연동 스모크, 결과는 .local-run/smoke.txt
scripts/verify-all.sh                전체 검증 [--slow]
scripts/test/lib.sh                  시험 도우미(확인 함수, 임시 작업 폴더, 가짜 서비스 기동)
scripts/test/fakes/fake-service.sh   셸 래퍼(자식·손자 프로세스, 환경·인자를 파일에 기록, 동작 모드)
scripts/test/fakes/fake-server.js    Node 가짜 서버(상태 확인, 프록시 경유 400, 웹훅·내부 경로 404)
scripts/test/run-local.test.sh       run-local.sh 시나리오 시험
scripts/test/smoke-local.test.sh     스모크 스크립트 자체 시험
scripts/test/verify-all.test.sh      verify-all.sh 단계 조합 시험
scripts/test/coverage.sh             xtrace 줄 커버리지 측정과 80% 하한 검사
scripts/test/run-all.sh              위 시험을 /bin/bash 로 차례로 실행
```

## Steps

- [x] Step 1: 골격. `scripts/` 폴더와 `scripts/test/lib.sh`, 가짜 서비스(`fake-service.sh`, `fake-server.js`)의 빈 뼈대, `scripts/run-local.sh`의 설정 구역과 함수 이름만 있는 뼈대(`set -euo pipefail`, `#!/usr/bin/env bash`)를 만든다. 실행 비트를 준다. (NFR8.1, NFR8.6, NFR9.1)
- [x] Step 2: 시험 실행 준비. `/bin/bash scripts/test/run-all.sh`와 단위 범위 명령 `/bin/bash scripts/test/run-local.test.sh`가 실행되는지 확인하고 `unit-test-instructions.md`에 기록한다. 카나리 시험: bash 3.2에서 `SHELLOPTS=xtrace`와 `PS4='+TRACE|${BASH_SOURCE}|${LINENO}|'`로 서브셸·자식 bash의 줄 번호가 보존되는지 확인한다(G1). (NFR7.1, NFR7.2)
- [x] Step 3: 설정·검증·비밀 값 - Red. 시험을 먼저 쓰고 실패 출력을 기록한다: 기본 포트와 `U1_PORT` 등 변경이 주소(`SEARCH_SERVICE_URL`, `RESERVATION_SERVICE_URL`, `WEBHOOK_URL`, `U1_URL`~`U3_URL`)에 반영, U3 포트 변경이 웹훅 주소에 반영, 잘못된 포트(문자, 1024 미만·65535 초과), 같은 포트 둘, 비루프백 `SERVER_ADDRESS`·`UI_HOST` 거부, `UI_ALLOW_NON_LOOPBACK` 거부, 비밀 값(생성 64자 이상, 사용자 값 32자 미만 거부, 공백·제어 문자·따옴표 거부, `openssl` 실패 시 `od` 대체, 둘 다 짧으면 거부). (NFR6.1, NFR6.3, NFR6.4, NFR8.1, G6)
- [x] Step 4: 설정·검증·비밀 값 - Green. `load_config`, `validate_config`, `derive_urls`, `make_secret`을 구현한다. `eval`을 쓰지 않고 모든 확장에 따옴표를 쓴다.
- [x] Step 5: 설정·검증·비밀 값 - Refactor. 함수 이름과 설정 구역을 정리하고 시험이 계속 통과함을 확인한다.
- [x] Step 6: 서비스 기동·환경 격리 - Red. 시험을 먼저 쓴다: 서비스별 환경 구성이 주입 변수 표와 같음(가짜 서비스가 자기 환경과 인자를 파일에 기록), `PAYMENT_WEBHOOK_SECRET`은 u3 가짜에만 있고 u1·u2·u4 가짜에는 없음, 사용자 셸의 같은 이름 변수가 상속되지 않음(G5), 비밀 값이 화면 출력·로그·명령줄 인자에 없음, 가짜 서비스가 받은 `SERVER_ADDRESS`·`UI_HOST`가 모두 127.0.0.1, `SHELLOPTS`가 서비스에 상속되지 않음(G2), 로그 파일 권한 0600과 두 번 실행해도 크기가 누적되지 않음, `.local-run/.gitignore` 내용 `*`. (NFR6.2, NFR6.3, NFR6.5, G2, G5)
- [x] Step 7: 서비스 기동·환경 격리 - Green. `start_service`와 로그 준비(`umask 077`)를 구현한다.
- [x] Step 8: 서비스 기동·환경 격리 - Refactor.
- [x] Step 9: 기동 흐름 - Red. 시험을 먼저 쓴다: 기동 성공(네 가짜가 u1 → u2 → u3 → u4 순서로 응답하고 접속 주소를 알리며 포그라운드 유지, Docker 명령 미사용), 포트 사용 중(어느 포트인지 알리고 거부), 서비스가 즉시 종료, 준비 시간 초과(제한을 환경 변수로 줄임, 마지막 20줄 로그를 보임), 준비되지 않은 빌드(`--prepare` 안내와 거부), 준비 판정(u1~u3 본문에 `"status":"UP"`, u4는 `/` 200과 프록시 점검이 정확히 400이어야 준비이고 500·502·404·연결 거부는 준비 아님, G3), 준비 시간 화면 출력, `--help`(환경 변수 목록·기본값·사용법), `--prepare`(가짜 도구 명령 호출 확인). (NFR8.2, NFR8.3, NFR8.5, NFR8.6, NFR8.9, NFR4.1, G3)
- [x] Step 10: 기동 흐름 - Green. `check_ports`, `check_prepared`, `wait_ready`, `prepare`, `usage`, `main`을 구현한다. 서비스는 각 유닛의 공개 실행 명령(`mvn -q -pl services/<서비스> spring-boot:run`, `npm --prefix services/u4-web-ui run dev`)만 쓴다.
- [x] Step 11: 기동 흐름 - Refactor.
- [x] Step 12: 종료와 감시 - Red. 시험을 먼저 쓴다: 중단 신호(Ctrl-C 대응 INT, TERM, HUP) 뒤 자식·손자 포함 프로세스와 네 포트가 없음, SIGTERM을 무시하는 가짜는 10초 뒤 SIGKILL로 끝남(유예를 환경 변수로 줄임), 기동 실패 때 이미 띄운 서비스 정리, 기동 뒤 가짜 서비스 종료를 2초 감시가 알리고 나머지를 정리하며 0이 아닌 종료 코드(감시 간격을 줄임), 정리 중 두 번째 신호를 보내도 정리가 한 번만 끝남과 남는 프로세스 없음(G7), 이미 끝난 그룹에 대한 `kill` 실패 허용, 그룹 ID가 `$!`와 같음. (NFR8.3, NFR8.4, NFR8.8, G7)
- [x] Step 13: 종료와 감시 - Green. `watch_services`, `cleanup`을 구현한다(`set -m`, `</dev/null`, 한 번만 실행 플래그, INT·TERM·HUP·EXIT trap, 정리 중 신호 무시).
- [x] Step 14: 종료와 감시 - Refactor.
- [x] Step 15: 스모크와 전체 검증 - Red. 시험을 먼저 쓴다: `smoke-local.sh`(가짜 서비스 응답으로 추천 → 결제 → `CONFIRMED` 5초 폴링, 단계별 결과와 시간을 `.local-run/smoke.txt`에 기록, 웹훅·내부 경로 미도달 판정 G4의 네 경우), `verify-all.sh`(가짜 명령으로 단계 성공·실패 조합, 실패해도 모든 단계 실행, 실패 단계 요약, 부하 평균 출력과 60 이상 경고, 재실행하지 않음, `--slow`이면 `-Pslow` 전달). (NFR8.7, NFR6.6, NFR7.3, G4, G9, G10)
- [x] Step 16: 스모크와 전체 검증 - Green. `scripts/smoke-local.sh`, `scripts/verify-all.sh`를 구현한다.
- [x] Step 17: 스모크와 전체 검증 - Refactor.
- [x] Step 18: 커버리지 측정기. `scripts/test/coverage.sh`를 시험부터 쓴다(작은 예제 스크립트로 분모 규칙 G1과 80% 미만 실패를 확인하는 시험), 이어서 구현한다. 모든 시나리오를 `COVERAGE=1`과 `SHELLOPTS=xtrace`로 돌려 `run-local.sh`의 줄별 실행 여부를 모아 비율을 계산하고 80% 미만이면 실패한다. `run-all.sh`가 일반 시험, 스모크·검증 시험, 커버리지를 차례로 실행한다. 하한은 낮추지 않는다. (NFR7.2, G1, G2, G8)
- [x] Step 19: 정적 검사와 환경 구성. `bash -n` 검사를 `run-all.sh`에 넣고(`shellcheck`가 있으면 함께, 없으면 건너뛴다고 알림), 모든 스크립트가 `/bin/bash` 3.2에서 도는지 확인한다. 비밀 값 패턴 검색(스크립트·시험에서 하드코딩된 비밀 값 0건)을 시험에 넣는다. (NFR9.1, NFR6.1)
- [x] Step 20: 문서와 추적성. `code-summary.md`(만든 파일, 실행 방법, 환경 변수 표, 커버리지 제외 규칙 목록과 근사 측정의 한계, 상류 문서를 대체한 결정 G3, 공유 계약 C1 400 확인 결과, Construction Verification Command 전환 제안 G9), `traceability.json`, `source-manifest.json`을 쓴다.

## 스토리 추적

| 스토리·인수 조건 | 계획 단계 |
|---|---|
| US5.1 AC5.1.1 (한 번에 기동, 접속 주소 안내, Docker 없음) | Step 9, 10, 12, 13 |
| US5.1 AC5.1.2 (추천 → 결제 → 예약 완료 연동 확인) | Step 15, 16 |
| US5.1 AC5.1.3 (기동 실패 원인과 정리) | Step 9, 10, 12, 13 |
| NFR6.1~NFR6.6 | Step 3~8, 15, 16, 19 |
| NFR7.1~NFR7.3 | Step 2, 15, 16, 18 |
| NFR8.1~NFR8.9 | Step 3~17 |
| NFR9.1, NFR4.1 | Step 1, 9, 10, 19 |

## Testing Contract

```json
{
  "version": 1,
  "methodology": "tdd",
  "source": "team",
  "ordering": "각 계층마다 실패하는 테스트를 먼저 작성한 뒤 그 테스트를 통과시키는 구현을 작성한다.",
  "scope": "classic",
  "test_strategy": "minimal",
  "project_type": "greenfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: tdd\n- **Ordering**: 각 계층마다 실패하는 테스트를 먼저 작성한 뒤 그 테스트를 통과시키는 구현을 작성한다.\n- `classic` 범위의 하한을 유지한다: 80% 라인 커버리지.\n- `classic` 범위의 하한을 유지한다: 병합 전 CI 실행. 하한은 약화할 수 없다."
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "tdd",
    "runner_step": "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - Red: write the failing tests and record the failing command output.",
      "Data model / database behavior - Green: implement only enough behavior to pass.",
      "Data model / database behavior - Refactor: improve the implementation while tests stay green.",
      "Repository / data access - Red: write the failing tests and record the failing command output.",
      "Repository / data access - Green: implement only enough behavior to pass.",
      "Repository / data access - Refactor: improve the implementation while tests stay green.",
      "Business logic - Red: write the failing tests and record the failing command output.",
      "Business logic - Green: implement only enough behavior to pass.",
      "Business logic - Refactor: improve the implementation while tests stay green.",
      "API / endpoint - Red: write the failing tests and record the failing command output.",
      "API / endpoint - Green: implement only enough behavior to pass.",
      "API / endpoint - Refactor: improve the implementation while tests stay green.",
      "Frontend behavior - Red: write the failing tests and record the failing command output.",
      "Frontend behavior - Green: implement only enough behavior to pass.",
      "Frontend behavior - Refactor: improve the implementation while tests stay green.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:569437ce643a91befd11b274b820beb938712e1b20ec466c3049ce509903f102",
  "contract_sha256": "sha256:296ad33f6f8e235b5c42f80437adace229248af280a22e4ef5ab043c7658107c"
}
```

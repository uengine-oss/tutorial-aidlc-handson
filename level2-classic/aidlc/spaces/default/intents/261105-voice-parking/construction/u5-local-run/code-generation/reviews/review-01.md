## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-08
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | scripts/run-local.sh > check_ports (줄 147), ports_still_open (줄 357) | `lsof`가 없으면 `lsof ... 2>&1` 실패가 "포트 비어 있음"으로 처리된다. 그러면 사용 중 포트 점검(NFR8.3)과 정리 뒤 포트 닫힘 확인(NFR8.4)이 아무 경고 없이 무력해진다. `curl`, `node`도 시작 전 존재 확인이 없다(`curl` 실패는 준비 판정이 영원히 "준비 아님"이 되어 90초 뒤 시간 초과로만 드러난다). | `main`에서 서비스를 띄우기 전에 `lsof`, `curl`, `mvn`, `npm`, `node` 존재를 확인하고, 없으면 어느 도구인지 알리고 종료 코드 2로 거부한다. 시험 한 개를 더한다. | New |
| R-02 | Minor | scripts/test/coverage.sh > denominator, run_full / scripts/run-local.sh 전체(`if ...; then ...; fi` 한 줄 27곳) | 분모 제외 규칙은 계획 G1의 목록과 정확히 같고(제외된 것은 함수 머리줄과 case 라벨뿐임을 292줄 분모와 대조해 확인) 규칙으로 비율을 올린 흔적은 없다. 그러나 G1이 정한 "한 명령 한 줄" 규칙 때문에 한 줄짜리 오류 분기 27곳이 조건만 평가돼도 실행된 줄로 세어진다. 보고된 100.0%(292 of 292)는 줄 도달률이고 분기 시험률이 아니다. 실제로 `cleanup`의 "정리 뒤에도 열려 있는 포트가 있습니다" 경고(줄 388)처럼 어떤 시험도 실행하지 않는 분기가 있다. G8에 한계가 적혀 있어 결함은 아니다. | `code-summary.md`의 커버리지 문장에 "100.0%는 줄 도달률이며 한 줄 분기 27곳은 분기 시험 여부를 보증하지 않는다"를 명시하고, 줄 388처럼 시험이 없는 분기(열린 포트 경고)에 시험을 하나 더한다. | New |
| R-03 | Minor | scripts/test/coverage.sh > run_full (줄 115~122), scripts/test/run-all.sh | 80% 하한 측정 대상은 `run-local.sh` 한 파일이다. 같은 유닛의 제품 스크립트 `smoke-local.sh`(168줄)와 `verify-all.sh`(117줄)는 시험은 있어도 줄 커버리지 하한 검사에 들어가지 않는다. 팀 규칙은 "80% 라인 커버리지"이고, 설계(NFR7.2 표)가 `run-local.sh`로 한정했으므로 계획 위반은 아니지만 하한이 유닛 스크립트의 절반 이하에만 걸려 있다. 또 `run_full`은 xtrace 모드 시나리오가 실패해도(`test_rc`) 커버리지 판정 종료 코드에 반영하지 않는다(일반 실행이 따로 잡는다). | `smoke-local.sh`, `verify-all.sh`도 같은 측정기로 재서 하한에 포함하거나, 포함하지 않는 이유를 `code-summary.md`에 명시한다. 포함하지 않는다면 이 한정이 팀 하한의 해석임을 사람이 승인하게 한다. | New |
| R-04 | Minor | scripts/smoke-local.sh > 설정 구역(줄 18~21), 줄 143 `deadline=$((SECONDS + CONFIRM_TIMEOUT))` | `SMOKE_CONFIRM_TIMEOUT`, `SMOKE_POLL_INTERVAL`, `SMOKE_BASE_URL`을 검증 없이 쓴다. 특히 `CONFIRM_TIMEOUT`은 산술 확장에 그대로 들어가는데, bash 산술은 값 안의 `a[$(cmd)]` 같은 식을 평가하므로 NFR6.4("설정값을 사용 전에 검증, eval·따옴표 없는 확장 금지")의 취지와 어긋난다. 환경을 쥔 쪽은 이미 같은 권한이라 실제 위험은 낮다. | `SMOKE_CONFIRM_TIMEOUT`은 `*[!0-9]*`이면, `SMOKE_POLL_INTERVAL`은 숫자 형식이 아니면 종료 코드 2로 거부하는 검증을 `run-local.sh`의 `validate_integer`/`validate_number`와 같은 방식으로 더하고 시험을 추가한다. | New |
| R-05 | Minor | scripts/verify-all.sh > 설정 구역(줄 12 `VERIFY_U5_SCRIPT`), scripts/test/coverage.sh 머리말 | `coverage.sh`는 하한을 환경 변수나 인자로 낮출 길이가 없음을 시험까지 하지만, `verify-all.sh`는 환경 변수 `VERIFY_U5_SCRIPT=/bin/true` 하나로 "u5 시험과 커버리지" 단계를 통째로 통과시킬 수 있다. 병합 전 검증 명령으로 제안되는 스크립트(G9)에 하한 우회 경로가 남는다. `RUN_LOCAL_ROOT`, `VERIFY_LOAD_AVG`도 같은 시험용 구멍이다. | 시험용 재정의는 `VERIFY_TEST_MODE=1` 같은 명시 신호가 있을 때만 읽거나, 재정의가 쓰이면 요약에 "u5 단계를 재정의함"을 크게 표시해 통과로 세지 않게 한다. | New |
| R-06 | Minor | scripts/run-local.sh > CLEAR_NAMES (줄 20), start_service | 환경 격리는 표에 있는 이름과 `UI_ALLOW_NON_LOOPBACK`, `SHELLOPTS`, `PS4`만 지운다. 사용자 셸의 `SPRING_APPLICATION_JSON`, `MANAGEMENT_SERVER_ADDRESS`, `JAVA_TOOL_OPTIONS`, `MAVEN_OPTS`, `BASH_ENV` 같은 변수는 서비스로 상속되어 `SERVER_ADDRESS=127.0.0.1` 주입(NFR6.3)을 우회할 수 있다(예: Spring은 `SPRING_APPLICATION_JSON`을 OS 환경 변수보다 우선한다). NFR6.2~6.3은 표 이름에 한정하고 G5도 그렇게 정했으므로 계획 위반은 아니고 잔여 위험이다. | `code-summary.md`의 한계 절에 "표에 없는 Spring·JVM 설정용 변수는 지우지 않는다"를 적거나, `SPRING_APPLICATION_JSON`, `MANAGEMENT_SERVER_ADDRESS`, `BASH_ENV`를 `CLEAR_NAMES`에 더하고 상속 차단 시험에 포함한다. | New |
| R-07 | Minor | scripts/test/lib.sh > run_case (줄 42~47) | 시험 함수가 중간에 비정상 종료(미설정 변수, 도구 실패 등)해도 그 앞에서 확인이 하나라도 기록됐으면 합격으로 센다. 서브셸 종료 코드를 보지 않고 케이스별 기대 확인 수도 없다. 현재는 79 case 전부가 정상 완주하므로 즉각 결함은 아니지만 "항상 통과하는 시험"으로 변질될 통로다. | `run_case`에서 서브셸 종료 코드가 0이 아니면 `FAIL <케이스> 시험 함수가 비정상 종료`를 기록한다. 마지막에 `end_scenario`/`suite_end` 도달 표지를 확인하는 방법도 있다. | New |
| R-08 | Minor | scripts/test/static.test.sh > case_product_script_conventions (줄 61), case_shellcheck_when_available (줄 98~99) | 줄 61은 정규식 `[a-z_]`와 `[a-z]`로 센 두 값이 사실상 같아 거의 항상 통과하는 확인이다(실제 확인은 바로 아래 줄 62). `shellcheck`가 없을 때 건너뜀을 `ok`로 기록해 통과 확인 수(39)에 포함한다. | 줄 61을 지우거나 줄 62와 합친다. 건너뜀은 `ok` 대신 별도 "SKIP" 출력으로 두어 확인 수에서 뺀다. | New |
| R-09 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u5-local-run/code-generation/traceability.json > upstream_ids, coverage | 계획의 스토리 추적표가 약속한 NFR7.1~7.3, NFR8.2, 8.4, 8.5, 8.6, 8.8, 8.9, NFR9.1, NFR4.1이 `upstream_ids`에 없다(12개만 있음). 구현은 시험 파일에서 확인되지만 추적 파일로는 NFR8.2(Docker 없음·한 번에 기동), NFR8.4(정리), NFR8.8(감시), NFR8.9(준비 판정), NFR7.x가 어느 파일로 닫혔는지 알 수 없다. | 빠진 NFR ID를 `upstream_ids`와 `coverage`(대상 파일 포함)에 더한다. | New |
| R-10 | Minor | scripts/run-local.sh > start_service (줄 218~220), cleanup | `&` 직후 `SVC_PIDS+=("$!")` 사이 짧은 틈에 신호가 오면 막 띄운 서비스가 추적 목록에 들어가기 전에 `exit`로 빠져, EXIT 정리가 그 서비스를 놓칠 수 있다. 또 SIGKILL 뒤 `wait "$pid"`는 신호를 무시하는 상태에서 무기한 막힐 수 있다(비정상적 D 상태 프로세스 한정). 둘 다 실사용 확률이 낮다. | 서비스를 띄우기 전에 신호 처리를 잠시 보류하거나(`trap '' INT TERM HUP`) PID를 기록한 다음 풀어 주고, 마지막 `wait`에 상한을 둔다. 낮은 우선순위. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `/bin/bash scripts/test/run-all.sh` (macOS bash 3.2.57, 한 번 실행, 가짜 서비스만 사용, 실제 mvn/npm 미실행) | 종료 코드 0. static 7 case/39, run-local 42 case/253, smoke-local 11 case/35, verify-all 10 case/31, coverage 9 case/81, 합계 79 case/439 assertion, 실패 0. 줄 커버리지 100.0% (292 of 292), 하한 80% 통과, 잘린 xtrace 0줄 | 작성자의 code-summary 수치와 일치한다. 100.0%는 위 Minor(줄 도달률)의 한계를 안고 읽어야 한다. `shellcheck` 미설치로 건너뜀. |
| `coverage.sh --denominator scripts/run-local.sh` 와 전체 비공백·비주석 줄 대조 | 분모 292줄. 분모에서 빠진 비주석 줄은 함수 머리줄, case 라벨, `fi/done/esac/}/;;/else/then/do` 단독 줄뿐 | 제외 규칙이 G1과 일치하고 실제 명령 줄을 빼서 비율을 올린 곳이 없다. |
| u1 SearchController / ErrorCode 대조 (G3 통합 지점 확인) | `POST /api/search/recommendations`는 세션 헤더가 유효하고 본문 `{}`이면 `text`가 비어 `INVALID_TEXT`(400)을 던진다. `MISSING_SESSION`, `INVALID_TEXT` 둘 다 400 | u4 준비 판정(정확히 400)이 u1 실제 동작에 맞는다. u1~u3 `application.properties`에 `management.endpoints.web.exposure.include=health`가 있어 `/actuator/health` 준비 판정도 성립한다. `vite.proxy.js`는 `/api/search`를 u1로 프록시한다. 실제 서비스 연동 증거는 Build and Test의 몫이라는 작성자 고지가 맞다. |
| 정적 점검(코드 읽기): `eval`, `docker`, 비밀 값 인자 전달, 따옴표 없는 확장 | `run-local.sh`에 `eval` 없음. 비밀 값은 `env NAME=value` 인자가 아니라 서브셸 `export`로 u3에만 전달되고 `exec env -u ...`가 나머지 서비스에서 지움. 로그는 `umask 077`(0600/0700), `>`로 덮어씀. 서비스는 `set -m` 아래 `$!`=그룹 ID로 띄우고 `kill -- -$pid`로 그룹 단위 종료. `cleanup`은 시작부터 끝까지 INT/TERM/HUP를 무시하고 1회만 실행. Docker 사용 없음 | NFR6.1~6.5, NFR8.1~8.4, G5, G7을 모두 충족한다. 위 Minor는 잔여 위험과 시험 품질에 한정된다. |

### Summary

Critical와 Major 결함은 찾지 못했다. 비밀 값 격리(u3에만 전달, 인자·로그·화면 없음), 루프백 고정, 입력 검증, 그룹 단위 정리, u4 정확히 400 판정, 커버리지 분모 규칙(G1) 모두 코드와 실행 결과(79 case, 439 assertion, 커버리지 100.0%, 실패 0)로 확인된다. 남은 사항은 Minor이며 가장 의미 있는 것은 커버리지 100.0%가 줄 도달률이라는 점, 하한이 `run-local.sh`에만 걸려 있다는 점, 도구 존재 확인과 `run_case` 비정상 종료 감지 부재다.

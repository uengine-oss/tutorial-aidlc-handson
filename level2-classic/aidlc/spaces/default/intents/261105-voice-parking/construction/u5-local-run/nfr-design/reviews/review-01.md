## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-08
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u5-local-run/nfr-design/security-design.md > 시험과 커버리지 측정 설계 > 커버리지 측정(분모 규칙) | 분모 규칙이 "빈 줄, 주석, fi/done/esac/}/;;/else/then/do만 있는 줄"만 제외한다. 그런데 xtrace는 실행된 명령만 찍으므로 함수 머리줄(`name() {`), `case` 라벨 줄(`a)`), ``로 이어진 뒤쪽 줄, `&&`·`\|\|`로 이어진 줄은 아무리 실행돼도 `+TRACE\|` 줄이 나오지 않는다. 이런 줄이 분모에 남으면 80% 하한이 정당한 시험으로도 달성되지 않거나, 반대로 구현자가 임의로 규칙을 늘려 하한을 약화한다. bash 3.2는 여러 줄 명령의 LINENO도 첫 줄이 아닌 값을 낼 수 있어 어느 줄이 실행으로 계산되는지도 정해져 있지 않다. 팀 규칙은 하한 약화를 금지한다. | 분모 규칙을 완결한다. 함수 머리줄·case 라벨·이어진 줄의 처리(제외하거나 첫 줄만 센다)와 여러 줄 명령의 LINENO 기준을 명시하고, 스크립트 작성 규칙(한 명령은 한 줄, 이어쓰기 금지 등)으로 분모를 안정시킨다. 제외 규칙을 `coverage.sh` 한곳에 두고 코드 요약에 목록을 적어 구현자가 하한을 임의로 줄이지 못하게 한다. 구현 첫 시험으로 3.2에서 줄 번호가 보존되는지(가정 3)와 이 규칙을 함께 확인하는 단계를 설계에 넣는다. | New |
| R-02 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u5-local-run/nfr-design/security-design.md > 시험과 커버리지 측정 설계 (SHELLOPTS=xtrace) 와 설계 결정 > 비밀 값 | `SHELLOPTS=xtrace`를 export한 환경에서 `run-local.sh`를 돌리면 비밀 값이 든 대입(`secret=$(...)`), 환경 구성 명령 줄, 서비스 래퍼의 명령 줄이 모두 xtrace로 stderr에 찍힌다. 이 stderr는 시나리오 파일로 가고, 서비스 래퍼가 상속받은 xtrace는 서비스 로그(`.local-run/logs/`)로도 간다. 그런데 같은 시험이 NFR6.2("출력·로그·인자에 값이 없음"), NFR6.5(로그 크기가 누적되지 않음)를 확인해야 한다. 커버리지 측정 방식과 비밀 값 비출력 시험이 서로 충돌하고, 추가로 `mvn`·`npm` 래퍼 스크립트와 가짜 서비스의 bash 호출까지 SHELLOPTS를 상속해 시끄러운 로그와 시간 지연이 생긴다. | 두 시험 종류를 분리하도록 설계한다. 비밀 값·로그·권한 시나리오는 xtrace 없이 실행하고 커버리지용 시나리오는 별도 실행으로 두거나, 비밀 값 변수를 xtrace에서 가리는 규칙(해당 대입 직전 `set +x`, 또는 PS4 필터와 파싱 단계의 마스킹)을 정한다. 서비스에는 xtrace 상속을 끊는(`env -u SHELLOPTS`) 경계를 `start_service`에 정의해 서비스 로그와 서브프로세스 소음을 막는다. 단 이 경우 서브셸·trap 처리기 수집(bullet 50번 줄의 주장)이 `run-local.sh` 자신의 서브셸만 대상임을 명시한다. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u5-local-run/nfr-requirements/tech-stack-decisions.md > 준비 확인 행, NFR8.9, 커버리지 측정 행 (설계와 불일치) | 설계는 u4 준비를 "프록시 경유 `POST /api/search/recommendations`가 정확히 400"으로 바꾸고 5xx 전체와 404를 거부한다. 그러나 상류의 준비 확인 행과 NFR8.9는 여전히 "502가 아닌 응답(405나 404 포함)"을 준비로 보고, 커버리지 행은 `BASH_XTRACEFD`를 명시한다. traceability.json은 NFR8.9와 NFR7.2를 OK로 표시해 문서 두 벌이 서로 다른 합격 기준을 말한다. 구현자가 어느 쪽에 맞출지 추측해야 한다. 400 자체는 u1 functional-spec(BR1.5 INVALID_TEXT, BR1.6 MISSING_SESSION이 먼저 평가됨)로 뒷받침되나 contract-summary.md의 C1 응답 표에는 200/422만 있었다는 u1 검토 기록(R-01)이 있어 공유 계약 쪽 반영 여부가 확인되지 않았다. | 설계가 정한 기준이 우선함을 상류 문서(NFR8.9, 준비 확인 행, 커버리지 측정 행)에 반영하거나 설계 문서에 "상류 문구를 대체한다"를 명시한다. 점검에 쓰는 세션 헤더 이름(`X-Session-Id`)과 UUID 값 생성 방법을 설계에 적고, C1의 400 응답이 공유 계약에 있는지 확인한다. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u5-local-run/nfr-design/security-design.md > 연동 스모크 (웹훅 미도달 판정) | 판정이 "u3 로그의 웹훅 경로 수신 줄 수가 같다"에 의존한다. 그러나 u3가 웹훅 요청(서명 실패 거절 포함)을 로그에 남긴다는 계약이나 줄 형식이 어디에도 없어 숨은 의존이다. u3가 로그를 남기지 않으면 이 조건은 항상 참이 되어 아무것도 검증하지 못한다. 또 `/internal/reservations`는 u2 경로인데 로그 비교는 u3만 본다. | 비교할 로그 줄 패턴을 u3의 공개된 동작에서 정하고(없으면 스모크가 조건을 "상태 코드 404/405만"으로 낮추고 로그 비교는 보조라고 명시), `/internal/reservations`는 u2 로그로 따로 비교한다. 단위 시험에서 가짜 u3 로그를 변조해 거짓 통과를 못 하는지 확인한다. | New |
| R-05 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u5-local-run/nfr-design/security-design.md > 설계 결정 > 환경 구성, 비밀 값 | (1) 상류는 "표에 없는 변수는 서비스에 넘기지 않는다"고 하나 설계는 `env -u`로 두 변수만 제거하므로 사용자의 셸에 있는 `WEBHOOK_URL`, `SEARCH_SERVICE_URL` 같은 값이 다른 서비스에 그대로 상속된다. (2) "값은 프로세스 인자에 넣지 않는다"고 하면서 `env NAME=value cmd` 형태를 쓰면 exec 전까지 `env`의 인자에 값이 보인다. (3) 사용자 비밀 값 최소 길이를 32자로 정했으나 생성 값 64자와 다른 근거가 없고 u3 자체의 최소 길이 요구와 대조하지 않았다. | (1) 주입 변수 표의 이름 전체를 먼저 `-u`로 지우고 다시 설정하거나 표 문구를 현실에 맞춘다. (2) 비밀 값은 명령 앞 대입이나 서브셸의 `export`로 전달한다고 명시한다. (3) 32자의 근거를 적고 u3 요구 길이와 일치함을 확인한다. | New |
| R-06 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u5-local-run/nfr-design/security-design.md > 설계 결정 > 프로세스 그룹과 정리 | `set -m`으로 서비스를 자기 그룹에 두면 터미널 Ctrl-C가 서비스 그룹에 가지 않고 스크립트 trap만 처리하므로 의도대로이나, 그룹 ID가 `$!`와 같다는 가정과 `trap '' INT TERM HUP`가 `cleanup` 중 SIGKILL 대기(10초)까지 포함하는지가 문서에 없다. 두 번째 Ctrl-C 시험은 목록에 있으나 합격 기준이 없다. | 그룹 ID 취득 방식과 정리 중 무시 구간을 명시하고, 두 번째 Ctrl-C 시험의 합격 기준(정리가 중단되지 않고 한 번만 끝남)을 적는다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| traceability.json 대조 | upstream_ids 20개 모두 OK, 단 NFR8.9·NFR7.2는 상류 문구와 설계가 불일치 | 위 Minor(상류 불일치)로 보고. 형식상 누락은 없음 |
| u1 functional-spec 지점 확인 (BR1.5, BR1.6) | `text` 비면 400 INVALID_TEXT, 세션 헤더 오류는 400 MISSING_SESSION | 설계의 400 점검 가정은 u1 명세로 뒷받침됨. 공유 계약 C1 반영 여부는 미확인 |
| 설계 코드 실행 | 해당 없음(설계 단계, 코드 없음) | bash 3.2 SHELLOPTS 줄 번호 보존은 가정 3대로 구현 첫 시험에서 확인해야 함 |

### Summary

memory.md의 열린 항목은 대체로 닫혔다: bash 3.2 xtrace 방식(BASH_XTRACEFD 제거), u4 준비 판정 400 한정(5xx 전체 거부), `set -m` 세부, 비밀 값 최소 길이, 웹훅 미도달 판정의 틀이 모두 들어 있다. 다만 커버리지 분모 규칙이 불완전해 80% 하한을 안정적으로 판정할 수 없고, xtrace 상속이 비밀 값 비출력 시험·서비스 로그와 충돌하는 Major 2건이 구현 전에 정리돼야 한다(Major 2건이므로 READY 기준 이내이나 코드 생성 계획에서 반드시 반영할 것).

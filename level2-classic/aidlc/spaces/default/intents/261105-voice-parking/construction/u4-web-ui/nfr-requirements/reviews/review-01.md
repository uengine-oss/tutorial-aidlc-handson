## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-07T15:44:25Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-requirements/performance-requirements.md > 판정 방법, NFR1.1, NFR3.1 | 문서는 "기준 수치는 낮추지 않고 통계만 정한다"고 하지만 실제로는 기준이 느슨해졌다. 기능 설계(functional-spec.md 154~158행)는 화면 몫을 "최댓값이 200ms 이하"로 정했는데, NFR1.1은 "p99 ≤ 100ms, 최댓값 ≤ 300ms"로 최댓값 한도를 300ms로 올렸다. 상위 요구사항 NFR1은 "100건 반복에서 최대 1초"로 최댓값 기준이다. 구간별 한도를 3배 최댓값까지 허용하면 구간 합이 1초를 넘을 수 있다(예: 화면 300 + 프록시 300 + u2·u3 각 150 이상). 그런데 NFR1.2(프록시)에는 최댓값 상한이 아예 없다. p99만으로는 NFR1의 최댓값 기준을 증명하지 못한다. | (1) "낮추지 않는다"는 문장을 사실에 맞게 고치거나, 최댓값 한도를 한도의 3배가 아니라 1초 예산을 지키는 값으로 정한다. (2) 이 구간별 p99 시험은 NFR1의 "100건 중 최대 1초" 판정을 대신하지 않으며, 최종 판정은 Build and Test의 합산 측정이 상위 요구사항의 최댓값 기준으로 한다고 명시한다. (3) NFR1.2에도 최댓값 상한을 둔다. | New |
| R-02 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-requirements/tech-stack-decisions.md > Assumptions & Open Questions, 대안(Playwright 제외) ; performance-requirements.md > 이 유닛이 맡지 않는 구간 | 실제 브라우저 `EventSource` 동작(자동 재연결, CLOSED 처리)과 1초 예산의 실제 화면 표시 구간을 "Build and Test의 통합 실행에서 확인한다"고 미룬다. 그러나 같은 문서가 브라우저 E2E(Playwright 등)를 대안에서 제외했고, 통합 실행에 브라우저를 쓰는 수단을 어디에도 정하지 않았다. jsdom에는 `EventSource`가 없어 NFR1.1·NFR1.4는 가짜 객체로만 시험된다. 또 NFR1.1은 가짜 이벤트 뒤 Vue의 비동기 DOM 갱신만 재므로 jsdom에서는 사실상 1~수 ms로, 100ms 한도를 어길 수 없는 시험이다. 결국 이 유닛의 어떤 시험도 브라우저의 실제 이벤트 처리와 렌더링을 재지 않는다. | 브라우저 쪽 확인 수단을 하나 정한다(예: 수동 확인 절차를 명시한 항목, 또는 통합 단계의 헤드리스 브라우저 점검). 정하지 않는다면 NFR1.1의 100ms가 jsdom에서는 "가상 이벤트 처리 지연 확인"일 뿐이라고 문서에 한계를 명시하고, 실제 브라우저 확인은 책임 소유자와 함께 열린 질문으로 남긴다. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-requirements/performance-requirements.md > NFR1.2 ; security-requirements.md > NFR6.3 | NFR1.2는 이벤트 20개를 하나의 p99로 판정한다. 표본 20개의 p99는 사실상 최댓값이고, "처음 20회 예열 버림, 100회 측정"이라는 문서 자신의 판정 규칙과도 다르다. 받는 쪽 클라이언트(Node 내장 `EventSource`는 실험 기능, 아니면 `http.get`)도 정하지 않았다. NFR6.3은 우회된 경로가 "404"여야 한다고 하는데, 실제 Vite 개발 서버는 프록시에 걸리지 않은 GET 요청(`Accept: */*` 또는 `text/html`)에 SPA 대체 응답(index.html, 200)을 돌려줄 수 있어 이 단언은 실패할 수 있다. functional-spec.md 159행은 같은 시험을 "이벤트 1초 간격 3개, http-proxy"로 적고 있어 두 문서가 어긋난다. | NFR1.2의 표본 수와 예열을 규칙에 맞추고 수신 클라이언트를 명시한다. NFR6.3의 합격 기준은 상태 코드가 아니라 "시험용 u3가 받은 요청 수 0"으로 바꾼다. 기능 설계의 프록시 시험 서술이 이 문서로 대체된다고 밝힌다. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-requirements/tech-stack-decisions.md > NFR7.2, 열린 질문 ; security-requirements.md > NFR6.7 | 프로젝트의 Construction Verification Command가 `mvn -q verify`라 이 유닛의 npm 시험·커버리지·린트가 병합 게이트에 들지 않는다는 점은 정확히 짚었다. 그러나 해결을 코드 생성 뒤 체크포인트로 미루는 동안 TDD·80% 하한이 어떤 명령으로 강제되는지는 비어 있다. `npm run verify`는 예시로만 나오고 정의가 없으며(정의된 스크립트는 `test:coverage`와 `lint`뿐), `npm audit`를 "병합 전 검증에서 출력"한다는 NFR6.7도 어느 명령에 속하는지, 취약점이 있어도 종료 코드를 실패로 하지 않을지 정하지 않았다. 레지스트리에 접근하지 못하면 audit 자체가 실패한다. | 체크포인트 전까지의 유닛 게이트를 `npm run verify` = 커버리지 시험 + 린트로 지금 정의하고(audit은 별도 보고 전용 스크립트), 체크포인트에서는 이것을 상위 명령에 묶는 일만 결정하도록 좁힌다. 커버리지 범위(`coverage.include`로 `src/**`와 프록시 모듈 포함, 제외 목록 명시)도 적어 하한이 제외로 무력화되지 않게 한다. | New |
| R-05 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-requirements/security-requirements.md > NFR6.1, NFR6.6, NFR6.7 | (a) NFR6.1의 소스 검색은 `v-html`과 `innerHTML`만 막는다. `insertAdjacentHTML`, `outerHTML`, `document.write`, 렌더 함수의 `innerHTML` 속성이 빠져 있다. (b) NFR6.6은 호스트를 환경 변수로 바꿀 수 있게 두어 `0.0.0.0` 설정을 막지 못하며, 루프백 바인딩이 개발 서버의 알려진 취약점(파일 접근 우회, DNS 리바인딩) 위험을 줄인다고 보지만 `server.allowedHosts`는 언급이 없다. (c) NFR6.7이 취약점을 "보고만 하고 사람이 판단한다"고 한 것은 이 유닛이 개발 서버 전용이라는 점에서 수용할 수 있으나, 위협 표의 유일한 의존성 방어책이 판정 기준 없는 출력이다. | (a) 금지 목록에 위 API들을 추가한다. (b) 호스트가 루프백이 아닐 때 경고하거나 거부하는 동작을 요구사항에 넣고 `allowedHosts` 기본값을 유지한다고 적는다. (c) 사람이 판단할 기준(높음·치명 취약점은 체크포인트 보고서에 기록)을 한 줄 추가한다. | New |
| R-06 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-requirements/performance-requirements.md > NFR3.2, NFR1.3 | NFR3.2는 클라이언트 제한 시간을 3초로 정하지만 상위 NFR3은 입력부터 추천 표시까지 2초다. 3초 제한 시간은 2초 목표를 넘겨도 기다리겠다는 뜻인데 이유가 적혀 있지 않다. NFR1.3의 "틱 한 번 p99 ≤ 5ms"는 문서가 경계하는 부하 민감 시험 중 한도가 가장 빡빡하고 최댓값 상한이 없다. | 3초의 근거(서비스 호출 제한 시간 2초 + 여유)를 적거나 값을 조정한다. 5ms는 지연 민감 시험의 예열·최댓값 규칙을 NFR1.3에도 똑같이 적용한다고 쓰거나, 한도를 완화하되 그 사실을 명시한다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 손 검증: 상위 NFR 포함 여부 | PASS | traceability.json의 `upstream_ids`가 NFR1~NFR9 전부이고 `coverage`에 9개 행이 모두 있다. requirements.md 69~77행의 NFR1~NFR9와 일치한다. |
| 손 검증: 대상 ID 존재 | PASS | OK 대상 NFR1.1~1.4, 3.1~3.2, 4.1, 5.1, 6.1~6.7, 7.1~7.2, 8.1~8.2, 9.1이 모두 산출물에 있다(NFR8.1은 security-requirements.md, NFR4.1·5.1·7.x·8.2·9.1은 tech-stack-decisions.md, NFR1.x·3.x는 performance-requirements.md). NFR2는 N/A이며 u2가 맡는다는 사유가 적혀 있다. |
| 손 검증: ID 상속·유일성 | PASS | 모든 NFRx.y가 부모 번호를 이어받고 중복이 없다. performance-requirements.md 표에서 NFR3.2 다음에 NFR1.3이 오는 순서만 어색하다(내용 문제 아님). |

### Summary

NFR 추적과 보안 구성은 대체로 견실하고 팀 규칙(Vue 3, ESLint+Prettier, TDD, 80%, Docker 금지, JavaScript)과 충돌하지 않는다. 가장 무거운 우려는 두 가지다. 하나는 "기준을 낮추지 않았다"는 설명과 달리 최댓값 한도가 200ms에서 300ms로 느슨해지고 구간별 p99가 NFR1의 최댓값 기준을 대신하지 못한다는 점이다. 다른 하나는 브라우저 `EventSource`와 실제 화면 구간을 재는 수단이 어디에도 정해져 있지 않다는 점이다. 이 두 건을 사람이 판단한 뒤 승인할 것을 권한다.

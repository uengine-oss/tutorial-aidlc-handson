## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-07T13:33:53Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u3-payment-service/nfr-requirements/performance-requirements.md > NFR1.5; reliability-requirements.md > NFR5.4 | NFR1.5는 "u2 예약 생성 호출의 제한 시간은 1.5초다"라고 하면서 출처를 "contract-summary.md의 제한 시간 표"로 적었다. 그러나 그 표는 "C5 예약 생성 \| 2초"이고 functional-design의 BR1.2도 "제한 시간 2초"다. 1.5초는 functional-design 검토(R-04)가 제안한 값일 뿐 계약과 규칙 어디에도 반영되지 않았다. 구현자가 BR1.2와 계약을 따르면 2초가 되어 NFR1.5의 "502까지 1.7초 이하" 시험이 실패하고, 2초 그대로면 호출자 u4의 2초 제한과 겹쳐 502가 사용자에게 닿지 않는다. | 1.5초를 확정값으로 삼을지 정하고, 그렇다면 BR1.2와 계약 표의 C5 예약 생성 제한 시간을 1.5초로 바꾸도록 상위 산출물 변경을 올린다. 그동안 NFR1.5와 NFR5.4의 출처에서 "계약 표"라는 표기를 고쳐 이 값이 새로 정한 값임을 밝힌다. | New |
| R-02 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u3-payment-service/nfr-requirements/security-requirements.md > NFR6.7 | NFR6.7은 "프록시는 `POST /api/payments`만 이 서비스로 보내고 `/api/payments/webhook`은 연결하지 않는다"고 요구한다. 그러나 계약(contract-summary.md)은 프록시를 "`/api/payments` → U3"라는 접두사 규칙으로 정했고, Vite 프록시의 문자열 키는 접두사 일치라 웹훅 경로까지 브라우저에 열린다. 이 요구는 u4·u5의 설정에 걸리는데 계약의 미결 사항이나 u4·u5가 받을 시험 기준으로 올라가 있지 않다. "루프백에만 연다"는 같은 머신의 Vite 프록시를 막지 못하므로 실제 방어선은 서명 검증 하나다. | NFR6.7이 정한 "웹훅 경로는 프록시에서 제외"를 u4·u5가 받는 시험 기준으로 계약의 미결/연동 확인 표에 올린다(예: 프록시 설정에 정확 일치 규칙 사용, 브라우저 경유 웹훅 호출이 404인지 확인). 올릴 수 없다면 서명 검증이 유일한 방어선이라는 점과 그 잔여 위험을 위협 고려 표에 명시한다. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u3-payment-service/nfr-requirements/scalability-requirements.md > NFR4.2; reliability-requirements.md > NFR5.5, NFR5.6 | NFR4.2는 "직렬화는 같은 eventId 안, 같은 (세션, 멱등 키) 안에서만 일어난다"고 못 박았다. 그러면 같은 Payment에 서로 다른 eventId(APPROVED와 FAILED)가 동시에 오는 경우 둘 다 종료 상태 확인(NFR5.6)을 통과한 뒤 u2 결과에 따라 Payment.status를 쓴다. BR3.5는 REJECTED일 때 Payment.status를 IGNORED로 바꾸므로 쓰기 순서에 따라 APPROVED가 IGNORED로 덮일 수 있다. 모의 PG는 결제당 이벤트를 하나만 보내므로 위조·시험 상황에 한정된다. | Payment 단위의 상태 갱신(종료 상태 확인과 status 쓰기)을 원자적으로 한다는 요구를 NFR5.6에 더하거나, 이 경쟁을 알려진 한계로 적고 동시 시험 항목을 하나 추가한다. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u3-payment-service/nfr-requirements/security-requirements.md > NFR6.5; tech-stack-decisions.md > 웹훅 본문 처리 | NFR6.5는 8KB 초과 본문을 "읽지 않고" 400으로 거부한다고 하지만, Content-Length가 없는 분할 전송 본문은 읽기 전에 크기를 알 수 없다. 기술 스택 표는 "원문 바이트를 먼저 읽고(8KB 상한)"라고만 하고 상한을 거는 방법을 정하지 않았다. 또 8KB 초과 시 400과 서명 불일치 시 401(NFR1.3) 중 무엇이 먼저인지도 없다. | 상한을 거는 방법을 정한다(예: 최대 8KB+1바이트까지만 읽는 한정 읽기, 길이 헤더 검사는 보조). 시험 항목에 길이 헤더 없는 본문과 서명 없는 초과 본문의 응답 코드를 더한다. | New |
| R-05 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u3-payment-service/nfr-requirements/reliability-requirements.md > NFR5.1 vs NFR5.3 | NFR5.1은 "u2 호출은 정확히 1회"라 하고, NFR5.3은 같은 반영 호출에 대해 최대 2회 재시도(총 3회 시도)를 허용한다. 문구가 서로 맞지 않아 시험 작성자가 "호출 1회"를 시도 횟수로 읽을 수 있다. 또 u2 반영이 성공한 뒤 eventId를 기록하기 전에 예외가 나면(NFR5.8) u2가 다시 호출되는데, 이 경우의 안전성(u2의 eventId 멱등)이 요구사항에 없다. | NFR5.1을 "성공한 반영은 정확히 1회(재시도는 같은 eventId)"로 고치고, u2의 eventId 멱등에 기대 재처리가 안전하다는 근거를 한 줄 적는다. | New |
| R-06 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u3-payment-service/nfr-requirements/performance-requirements.md > NFR1.6, 자원 제약; scalability-requirements.md > NFR4.5 | 웹훅 처리가 u2 저하로 최악 약 6.4초 걸릴 수 있다고 인정하는데, 모의 PG가 웹훅을 부르는 호출의 제한 시간과 스케줄러 스레드 수가 정해져 있지 않다. 호출이 스케줄러 스레드를 붙들면 NFR4.5의 "지연 알림 50건이 제때 발송"과 NFR1.6의 DELAY 오차 ±1초가 u2 저하 때 깨진다. NFR1.6의 측정 방법("주입 시계와 실제 스케줄러 시험")도 합격 기준이 모호하다. | 모의 PG 자기 호출의 제한 시간과 스케줄러 스레드 수(또는 발송을 별도 실행기로 넘기는 것)를 정하고, NFR1.6의 측정 방법을 횟수와 최댓값 기준으로 구체화한다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 상위 NFR 커버리지(수작업) | PASS | traceability.json의 upstream_ids "NFR1"~"NFR9"가 requirements.md의 NFR1~NFR9와 같고, coverage 행이 9개(NFR2, NFR3은 N/A와 사유, 나머지 OK) 있다. |
| OK 대상 ID 존재(수작업) | PASS | NFR1.1~1.6(performance), 1.7~1.9(observability), NFR4.1~4.5(scalability), NFR5.1~5.11(reliability)와 5.12~5.14(observability), NFR6.1~6.8(security), NFR7.1·NFR8.2·NFR9.1(tech-stack), NFR8.1(security)이 모두 해당 파일에 있다. |
| ID 유일성(수작업) | PASS | 파일 사이 중복 ID가 없다. NFR1.x·NFR5.x·NFR8.x는 두 파일에 나뉘어 있으나 번호가 겹치지 않는다. |
| 부모 번호 상속(수작업) | PASS | 모든 NFRx.y가 부모 번호 x를 따른다. |
| 기능 설계 검토 지적 반영(수작업) | PASS | 같은 키 동시성은 NFR5.5, 종료된 Payment와 새 eventId는 NFR5.6, 150ms 범위는 NFR1.1("u2가 정상 응답하는 경우에 한한다")에 반영돼 있다. |

### Summary

보안·신뢰성·성능 목표는 대체로 측정 가능하고 팀 규칙(Java 17, Maven, Docker 금지, TDD와 JaCoCo 80%)과 맞는다. 다만 C5 예약 생성 제한 시간 1.5초가 계약 표와 BR1.2의 2초와 어긋나고, 웹훅 경로를 프록시에서 빼야 한다는 NFR6.7이 접두사 방식 프록시 계약과 충돌한 채 u4·u5로 전달되지 않는 두 가지는 승인 전에 정리하는 것이 좋다. 나머지는 구현 중에 정해도 되는 보완 사항이다.

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-07T12:16:49Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/functional-design/rules.md > BR1.2 | BR1.2는 같은 멱등 키 재요청에 "현재 status(결제 대기가 아닐 수 있다)"를 200으로 돌려준다고 한다. 그러나 계약 C5는 200 본문이 "201과 같다"고 하고 201 본문의 `status`를 `enum: [PENDING_PAYMENT]`로 고정한다. C2의 202 응답도 `status`가 PENDING_PAYMENT뿐이다. u3가 이 200 응답을 받아 그대로 중계하면 CONFIRMED, PAYMENT_FAILED, CANCELLED 값이 계약을 어긴다. 또 u3가 종료된 예약을 받았을 때 모의 PG에 다시 결제를 요청해야 하는지도 정해져 있지 않다. | 둘 중 하나로 정한다. (a) 200 응답의 `status`가 네 상태 중 어느 것이든 될 수 있다고 C5(소유자 U2)를 고치고 u3에 알린다. (b) 이 서비스가 항상 PENDING_PAYMENT를 돌려주도록 BR1.2를 바꾼다. 어느 쪽이든 기존 예약이 이미 종료 상태일 때 u3가 해야 할 동작을 한 줄로 못 박고 시험 기준에 "종료된 예약의 같은 키 재요청" 행을 추가한다. | New |
| R-02 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/functional-design/functional-spec.md > W1 5단계 (BR1.2, BR1.3, BR5.2) | 같은 키의 요청이 동시에 두 번 오면(C2는 시간 초과 뒤 같은 키 재전송을 권장한다) 둘 다 2~3단계를 통과한다. 5단계는 "사이에 다른 예약이 생겼으면 409 PENDING_EXISTS"로만 적혀 있어, 뒤에 직렬화된 같은 키 요청이 409를 받을 수 있다. BR1.2는 같은 키면 200으로 기존 예약을 돌려주라고 한다. BR5.2는 "BR1.2, BR1.3의 검사와 저장이 끼어들지 않는다"고 하지만 W1은 직렬화 안에서 BR1.3만 다시 검사한다. 시험 기준에 동시 같은 키 장면이 없어 TDD에서 결과가 둘로 갈린다. | 세션 직렬화 안에서 BR1.2(멱등 키 조회)를 먼저, 그다음 BR1.3을 검사한다고 W1 5단계와 BR1.3에 적는다. 이 경우 두 번째 요청은 200이고 u1 호출 한 번이 낭비될 뿐이라는 점도 적는다. 시험 기준 요약에 "같은 키 동시 2건: 예약 1건, 201 하나와 200 하나, 타이머와 푸시 1회" 행을 추가한다. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/functional-design/rules.md > BR2.1 (violation, logic), functional-spec.md > 오류 응답 정리 | functional-spec.md는 결제 결과 요청 형식 오류를 "400 INVALID_REQUEST \| BR2.1"로 표에 올렸다. 그러나 BR2.1의 `violation`은 404만 적고 `logic`에는 400 검증이 없다. BR1.1은 `applies_to`가 예약 생성뿐이다. eventId 1~100자와 outcome 허용값은 entities.md의 PaymentResultCommand에만 있다. 또 C5는 두 엔드포인트 어디에도 400 응답을 선언하지 않았다. | BR2.1에 검증 단계(eventId 1~100자, outcome이 APPROVED 또는 FAILED, 위반 시 400 INVALID_REQUEST)를 추가하거나 BR1.1과 같은 별도 규칙으로 분리한다. 400을 C5에 응답으로 더하도록 u3와 합의한다(필드 추가 규칙상 안전). 지금 상태로는 u3가 400을 계약 밖의 실패로 보고 502로 바꿀 수 있다. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/functional-design/rules.md > BR4.3, functional-spec.md > W4 구독 3~4단계 | 구독 등록, 최신 스냅샷 조회, 첫 스냅샷 전송이 새 푸시와 어떤 순서로 일어나는지가 없다. 등록 전에 읽으면 그 사이의 version 2를 놓치고, 등록 후 읽으면 새 푸시가 첫 스냅샷보다 먼저 나가 한 연결에서 version이 거꾸로 갈 수 있다. 또 LatestSnapshot이 없을 때 처리부가 만든 스냅샷을 LatestSnapshot에 저장하는지도 불분명하다. | "예약 단위 직렬화 안에서 구독을 등록하고 같은 시점의 스냅샷을 읽어 그 연결로 먼저 쓴다"고 정한다. 대체 스냅샷은 BR4.2의 version 규칙으로 저장한다고 적는다. 시험 기준에 "구독 직전·직후에 상태가 바뀌어도 version이 낮아지지 않고 누락도 없다" 행을 더한다. | New |
| R-05 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/functional-design/entities.md > Reservation.relationships, "components.md와 달라진 점" | (1) `Reservation -> AppliedPaymentEvent`는 "1..*"인데 note는 "0건일 수 있다"고 한다. (2) `Reservation -> LatestSnapshot`은 "1..1"인데 BR4.3과 BR5.3은 스냅샷이 없는 경우를 전제한다. (3) components.md는 스냅샷이 없으면 "화면이 Reservation에서 현재 상태를 직접 조회한다"고 했는데, 이 설계는 구독 처리부가 대신 만들어 보내는 방식으로 바꿨고 "달라진 점"에 이 변경이 빠졌다. | 카디널리티를 0..*와 0..1로 고친다. "달라진 점"에 구독 처리부의 대체 스냅샷 생성 방식과 그 이유(재연결 시 화면 왕복 제거, Notification은 Reservation을 호출하지 않음)를 한 줄 더한다. | New |
| R-06 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/functional-design/rules.md > BR2.3, BR2.4 | `cancelledAt`은 "지금"으로 정하므로 BR2.3이 마감 후 결과를 발견해 취소하는 경로에서는 결과 도착 시각이 된다. 한편 BR2.4는 취소 시각이 180초 ± 2초 안이어야 한다고 모든 경로에 적었다. 타이머가 2초 넘게 늦으면 이 경로의 cancelledAt이 범위를 벗어나 AC4.2.4와 어긋난다. 또 판정 기준이 u2가 요청을 받은 시각이라 웹훅이 179.9초에 u3에 닿았어도 u2에서 180초를 넘으면 취소된다. | 늦게 발견한 취소의 `cancelledAt`을 마감 시각으로 고정할지 지금으로 둘지 정하고 한 줄 근거를 적는다. 마감 판정이 u2 수신 시각 기준이라는 점을 BR2.3의 가정으로 명시한다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| sensor-traceability | 실행하지 않음. 읽기 전용 여부를 확인하지 않았고 손으로 검증했다 | 아래 수동 검증으로 대체했다. 도구 실행 결과가 아니므로 게이트의 sensor 결과를 함께 보면 좋다. |
| 수동: AC 커버리지 | PASS | `upstream_ids`에 AC3.1.1~3, AC3.2.1~3, AC4.1.1~5, AC4.2.1~4 15건이 모두 있고 `coverage`에 각각 한 행씩 있다. stories.md의 이 네 스토리 인수 조건과 개수가 맞다. |
| 수동: OK 대상 존재 | PASS | OK 행의 대상(BR1.2~1.5, BR2.1~2.6, BR3.3, BR4.1, BR4.3, BR4.6, BR5.1, BR5.2)이 모두 rules.md에 있다. AC4.1.3은 N/A이며 u3 소관이라고 사유를 적었다. |
| 수동: 규칙 고아 여부 | PASS | rules.md의 BR1.1~BR5.3 가운데 coverage 대상에 쓰이지 않은 규칙은 `reverse`에 N/A 사유와 함께 올라 있다(BR1.1, 1.4, 2.7, 3.1, 3.2, 4.2, 4.4, 4.5, 5.3). 설명 없는 고아 규칙은 없다. |
| 수동: 계약 C3/C4/C5 대조 | 대체로 일치 | 필드 이름, 이벤트 이름 `snapshot`과 id=version, 401과 404 코드, C4 오류 전달(404, 422, 502 변환), 재시도 값이 일치한다. 불일치는 위 Major 1건(BR1.2 status)과 Minor 1건(400 미선언)이다. |
| 수동: 180초 경계와 경합 | 대체로 PASS | BR2.3의 `>=` 비교는 AC4.2.2(179초 CONFIRMED)와 AC4.2.3(181초 CANCELLED)에 맞고, 시계를 주입해 시험한다(BR5.1). 타이머가 늦은 경우도 시계로 판정하므로 AC4.1.5와 모순이 없다. 남은 허점은 위 Minor(cancelledAt, u2 수신 시각 기준)뿐이다. |
| 수동: 엔티티 소유권 | PASS | LatestSnapshot을 Subscription에서 분리해 Notification 소유로 둔 것과 AppliedPaymentEvent를 Reservation 소유로 추가한 것은 C5 멱등 요구로 근거가 있고 "달라진 점"에 적혀 있다. 단 구독 처리부의 대체 스냅샷 생성은 설명이 빠졌다(위 Minor). |

### Summary

계약 C3/C4/C5와의 정합성, 180초 경계와 직렬화 규칙, 15개 인수 조건의 추적은 대체로 견고하고 구현자가 스스로 채워야 할 큰 틈은 없다. 승인 전에 BR1.2의 200 응답 status가 C5 enum과 충돌하는 점과 같은 키 동시 요청의 결과(409인지 200인지)를 정하길 권한다. 나머지 Minor는 구현 전에 한 줄씩 정리하면 TDD 모호함이 줄어든다.

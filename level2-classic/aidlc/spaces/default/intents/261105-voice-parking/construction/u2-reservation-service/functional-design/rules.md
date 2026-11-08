# u2-reservation-service 비즈니스 규칙

## Sources

- [scope] Workflow-selected scope: classic
- [Q3] 결제 승인은 비동기 웹훅, 승인 후 1초 안에 화면에 예약 완료, 180초 안에 승인이 없으면 자동 취소 (requirements-analysis Q3)
- [Q6] 화면 전달은 SSE 서버 푸시, 1초는 웹훅 수신 시각부터 화면 표시까지 (requirements-analysis Q6)
- [Q7] 180초는 결제 요청 시점부터, 취소 시 같은 푸시 방식으로 안내 (requirements-analysis Q7)
- [memory:M1] requirements.md(FR4~FR8, NFR1, NFR2, NFR5), stories.md(US3.1~US4.2), components.md(Reservation, Notification과 ADR-002), contract-summary.md(C3, C4, C5)

```yaml
rules:
  - id: BR1.1
    statement: "예약 생성 요청은 형식이 맞아야 한다."
    category: validation
    applies_to: POST /internal/reservations
    trigger: "예약 생성 요청을 받을 때"
    logic: "IF sessionId가 UUID 형식이 아니거나, passId 또는 idempotencyKey가 비었거나(공백만 포함), durationHours가 1 미만이거나 24 초과이면 THEN 400 INVALID_REQUEST를 돌려주고 아무것도 만들지 않는다."
    violation: "400 INVALID_REQUEST"
    source: [FR4.1, FR8.1]
  - id: BR1.2
    statement: "같은 세션의 같은 멱등 키 요청은 새 예약을 만들지 않고 기존 예약을 그대로 돌려준다."
    category: policy
    applies_to: POST /internal/reservations
    trigger: "예약 생성 요청을 받을 때 (BR1.1 통과 뒤 가장 먼저 확인)"
    logic: "IF (sessionId, idempotencyKey)로 이미 만든 예약이 있으면 THEN 그 예약의 reservationId, 현재 status(결제 대기가 아닐 수 있다), amount, durationHours, lotName, paymentRequestedAt을 200으로 돌려준다. 새 예약도, 새 타이머도, 새 푸시도 만들지 않는다. 기존 예약이 있으면 BR1.3의 결제 대기 제한은 검사하지 않는다."
    violation: "해당 없음"
    source: [FR4.2]
  - id: BR1.3
    statement: "한 세션에는 결제 대기 예약이 동시에 1건만 있을 수 있다."
    category: constraint
    applies_to: POST /internal/reservations
    trigger: "BR1.2에서 기존 예약이 없을 때"
    logic: "IF 같은 sessionId에 status가 PENDING_PAYMENT인 예약이 있으면 THEN 새 예약을 만들지 않고 409 PENDING_EXISTS를 돌려준다. 본문은 공통 오류 형식에 기존 예약의 reservationId를 더한다. 이 검사와 새 예약 저장은 같은 세션에 대해 하나의 원자 연산이다(BR5.2). 주차권 조회(BR1.4) 전에 한 번 검사해 불필요한 호출을 줄이고, 저장 직전에 다시 검사한다."
    violation: "409 PENDING_EXISTS"
    source: [FR4.2]
  - id: BR1.4
    statement: "예약 금액과 주차장 이름은 주차권 조회(C4) 결과로 정하고 클라이언트가 보낸 값은 받지 않는다."
    category: calculation
    applies_to: Reservation.amount, Reservation.lotName
    trigger: "BR1.3 통과 뒤"
    logic: "u1-search-service의 주차권 조회를 passId와 durationHours로 호출한다. 성공하면 amount = 응답의 price, lotName = 응답의 lotName으로 저장한다. u1이 PASS_NOT_FOUND를 돌려주면 404 PASS_NOT_FOUND, DURATION_NOT_AVAILABLE을 돌려주면 422 DURATION_NOT_AVAILABLE을 그대로 전한다. 제한 시간 2초와 200ms 간격 최대 2회 재시도를 지켰는데도 응답을 받지 못하거나 그 밖의 오류이면 502 UPSTREAM_FAILED를 돌려준다. 어느 경우에도 예약은 만들어지지 않는다."
    violation: "404 PASS_NOT_FOUND, 422 DURATION_NOT_AVAILABLE, 502 UPSTREAM_FAILED"
    source: [FR4.1, FR4.2]
  - id: BR1.5
    statement: "새 예약은 결제 대기 상태로 시작하고 180초 마감 시각이 정해진다."
    category: policy
    applies_to: Reservation
    trigger: "BR1.4 성공 뒤 저장할 때"
    logic: "reservationId를 새로 만들고 status = PENDING_PAYMENT, version = 1, paymentRequestedAt = 저장 시각(서버 시계, UTC), message = 결제 대기 안내로 저장한다. 마감 시각 = paymentRequestedAt + 180초이며 이 시각에 자동 취소(BR2.4)를 예약한다. 저장이 끝나면 첫 스냅샷(version 1)을 Notification에 푸시한다(BR4.1). 201을 돌려준다."
    violation: "해당 없음"
    source: [FR4.2, FR4.3, FR7.1]
  - id: BR2.1
    statement: "결제 결과 반영은 예약이 결제 대기일 때만 적용되는 원자 연산이다."
    category: policy
    applies_to: POST /internal/reservations/{reservationId}/payment-result
    trigger: "결제 결과 반영 요청(eventId, outcome)을 받을 때"
    logic: "예약 하나에 대한 판정은 한 번에 하나만 진행한다(BR5.2). 순서: (1) 예약이 없으면 404 NOT_FOUND. (2) 같은 eventId의 AppliedPaymentEvent가 이미 있으면 처음 낸 result와 reservationStatus를 그대로 돌려주고 상태도 푸시도 바꾸지 않는다. (3) status가 PENDING_PAYMENT이고 마감 전이면(BR2.3) outcome이 APPROVED일 때 status = CONFIRMED, confirmedAt = 지금, FAILED일 때 status = PAYMENT_FAILED, failedAt = 지금으로 바꾸고 version을 1 올리고 message를 갱신한 뒤 result = APPLIED로 판정한다. (4) 그 밖에는 BR2.2. 판정은 AppliedPaymentEvent로 기록하고, APPLIED이면 새 스냅샷을 푸시한다(BR4.1). 응답은 result와 판정 직후의 reservationStatus다."
    violation: "404 NOT_FOUND (예약 없음)"
    source: [FR5.2, FR5.3, FR5.5, NFR5]
  - id: BR2.2
    statement: "이미 확정·실패·취소된 예약에 온 결제 결과는 상태를 바꾸지 않고 거부하며 기록한다."
    category: policy
    applies_to: applyPaymentResult
    trigger: "BR2.1에서 status가 PENDING_PAYMENT가 아니거나 마감이 지났을 때"
    logic: "IF 예약의 status가 CONFIRMED, PAYMENT_FAILED, CANCELLED이면 THEN 상태와 version을 바꾸지 않고 result = REJECTED, 현재 reservationStatus와 함께 돌려준다. 거부 사실은 AppliedPaymentEvent로 남기고 예약 ID, eventId, outcome, 현재 상태를 로그에 기록한다. 푸시는 하지 않는다. 특히 CANCELLED 예약에 늦게 온 APPROVED는 되살리지 않는다."
    violation: "해당 없음 (이 규칙이 거부 처리다)"
    source: [FR5.4, FR5.3]
  - id: BR2.3
    statement: "180초 마감 시각 이후의 결제 결과는 타이머가 늦더라도 먼저 취소로 확정한 뒤 거부한다. 마감과 같은 시각이면 취소가 이긴다."
    category: constraint
    applies_to: applyPaymentResult, 자동 취소
    trigger: "결제 결과를 반영하려는 시점에 status가 PENDING_PAYMENT일 때"
    logic: "IF 지금 >= paymentRequestedAt + 180초 THEN 이 요청의 outcome과 관계없이 먼저 BR2.4의 취소를 수행해 CANCELLED로 확정하고, 이어서 BR2.2에 따라 REJECTED를 돌려준다. IF 지금 < paymentRequestedAt + 180초 THEN 결과를 적용한다(179초의 승인은 CONFIRMED가 된다). 두 일이 동시에 도착하면 BR5.2의 직렬화로 먼저 판정을 시작한 쪽이 이기고 나머지는 그 결과를 본다. 마감 판정은 타이머가 실제로 울렸는지가 아니라 시계로 한다."
    violation: "해당 없음"
    source: [FR5.4, FR7.1, NFR2]
  - id: BR2.4
    statement: "결제 요청 시점부터 180초 안에 승인이 없으면 예약은 자동으로 취소되고 안내 문구가 붙는다."
    category: policy
    applies_to: Reservation
    trigger: "마감 시각(paymentRequestedAt + 180초)에 타이머가 울릴 때, 또는 BR2.3이 마감 후 요청을 발견했을 때"
    logic: "IF status가 PENDING_PAYMENT이면 THEN status = CANCELLED, cancelledAt = 지금, version을 1 올리고 message = '승인 지연으로 예약이 취소되었습니다'로 바꾼 뒤 스냅샷을 푸시한다(BR4.1). IF status가 이미 CONFIRMED, PAYMENT_FAILED, CANCELLED이면 THEN 아무것도 하지 않는다. 타이머는 마감 시각에 정확히 한 번 울리도록 예약하고, 예약이 결제 대기를 벗어나면 취소(해제)한다. 취소 시각은 paymentRequestedAt에서 180초 ± 2초 안이어야 한다."
    violation: "시험 실패 (취소가 178~182초 밖)"
    source: [FR7.1, FR7.2, NFR2]
  - id: BR2.5
    statement: "확정·결제 실패·취소는 종료 상태이며 한 번 들어가면 바뀌지 않는다."
    category: constraint
    applies_to: Reservation.status
    trigger: "모든 상태 변경 시도"
    logic: "허용된 전이는 PENDING_PAYMENT -> CONFIRMED, PENDING_PAYMENT -> PAYMENT_FAILED, PENDING_PAYMENT -> CANCELLED 세 가지뿐이다. 그 밖의 전이 시도는 상태를 바꾸지 않는다. 시간이 지나도 PAYMENT_FAILED가 CONFIRMED로 바뀌거나 CANCELLED가 되살아나지 않는다."
    violation: "해당 없음 (시도가 무시되거나 BR2.2로 거부된다)"
    source: [FR5.4, FR5.2]
  - id: BR2.6
    statement: "결제 실패한 예약은 새 예약을 막지 않는다. 같은 세션이 다른 후보로 새 예약을 만들 수 있다."
    category: policy
    applies_to: BR1.3
    trigger: "세션에 PAYMENT_FAILED 예약이 있고 새 예약 요청이 올 때"
    logic: "BR1.3의 결제 대기 제한은 status가 PENDING_PAYMENT인 예약만 센다. PAYMENT_FAILED와 CANCELLED와 CONFIRMED는 세지 않는다. 새 예약은 새 idempotencyKey로 새 reservationId를 받으며 실패한 예약은 그대로 남는다."
    violation: "해당 없음"
    source: [FR5.5]
  - id: BR2.7
    statement: "예약을 확정해도 주차권 재고를 차감하지 않는다."
    category: policy
    applies_to: Reservation
    trigger: "CONFIRMED로 바뀔 때"
    logic: "재고를 관리하지 않는다. 같은 주차권을 여러 세션이 각각 예약할 수 있다."
    violation: "해당 없음"
    source: [FR5.2]
  - id: BR3.1
    statement: "예약 조회와 구독은 세션 식별자가 있어야 한다."
    category: authorization
    applies_to: GET /api/reservations/current, GET /api/reservations/{reservationId}, GET /api/reservations/{reservationId}/events
    trigger: "조회·구독 요청을 받을 때"
    logic: "IF 헤더 X-Session-Id가 없고 (구독 엔드포인트의 경우) 쿼리 sessionId도 없으면 THEN 401 MISSING_SESSION. IF 값이 UUID 형식이 아니면 THEN 401 MISSING_SESSION. 구독 엔드포인트에서 헤더와 쿼리가 모두 있으면 헤더가 우선한다."
    violation: "401 MISSING_SESSION"
    source: [FR8.1]
  - id: BR3.2
    statement: "세션은 자기 예약만 볼 수 있다. 다른 세션의 예약은 없는 것으로 대한다."
    category: authorization
    applies_to: 예약 단건 조회, 구독
    trigger: "reservationId로 조회·구독할 때"
    logic: "IF 예약의 sessionId가 요청의 세션과 다르면 THEN 404 NOT_FOUND를 돌려준다(403으로 존재를 알려 주지 않는다). 구독도 같은 검사를 통과해야 연결된다."
    violation: "404 NOT_FOUND"
    source: [FR8.1]
  - id: BR3.3
    statement: "현재 예약 조회는 그 세션에서 가장 최근에 만든 예약의 현재 상태를 돌려준다."
    category: policy
    applies_to: GET /api/reservations/current
    trigger: "화면 새로 고침이나 재진입 때"
    logic: "IF 세션의 예약이 있으면 THEN paymentRequestedAt이 가장 늦은 예약(같으면 나중에 저장된 것)의 현재 상태를 200으로 돌려준다. 이 값은 SSE 푸시 없이도 같은 정보를 준다. IF 세션의 예약이 없으면 THEN 404 NO_RESERVATION."
    violation: "404 NO_RESERVATION"
    source: [FR6.3, FR8.1]
  - id: BR4.1
    statement: "예약 상태가 바뀔 때마다 현재 상태 전체를 스냅샷으로 Notification에 밀어 넣는다."
    category: policy
    applies_to: Notification
    trigger: "예약 생성(version 1), CONFIRMED, PAYMENT_FAILED, CANCELLED로 바뀐 직후"
    logic: "상태 변경이 저장된 뒤에 계약 C3의 Reservation 형식과 같은 스냅샷(version 포함)을 Notification에 전달한다. 같은 예약의 스냅샷은 version 순서대로 전달한다. 상태가 바뀌지 않은 판정(BR2.2의 거부, 중복 eventId)은 푸시하지 않는다. 스냅샷의 message는 상태별 안내 문구다."
    violation: "해당 없음"
    source: [FR6.1, FR6.2, FR7.2]
  - id: BR4.2
    statement: "Notification은 예약마다 가장 높은 version의 스냅샷만 보관한다."
    category: constraint
    applies_to: LatestSnapshot
    trigger: "스냅샷을 받을 때"
    logic: "IF 받은 스냅샷의 version이 보관 중인 것보다 높으면 THEN 교체하고 구독 중인 모든 연결에 보낸다. IF 같거나 낮으면 THEN 버리고 보내지 않는다."
    violation: "해당 없음"
    source: [FR6.1, FR6.3]
  - id: BR4.3
    statement: "구독하면 보관 중인 최신 스냅샷을 먼저 한 번 보내고 이후 변화를 이어서 보낸다."
    category: policy
    applies_to: GET /api/reservations/{reservationId}/events
    trigger: "화면이 예약을 구독하거나 끊긴 뒤 다시 구독할 때"
    logic: "BR3.1, BR3.2를 통과하면 연결을 열고 이벤트 이름 snapshot, id = version, 데이터 = 스냅샷 JSON으로 최신 스냅샷을 먼저 한 번 보낸다. 보관 중인 스냅샷이 없으면(예: Notification이 아직 받지 못함) 요청을 처리하는 쪽이 Reservation의 현재 상태에서 스냅샷을 만들어 대신 보낸다. Notification 자신은 Reservation을 호출하지 않는다. 재구독 때도 같다. 종료 상태의 예약도 구독할 수 있고 그 상태를 한 번 받는다."
    violation: "401 MISSING_SESSION, 404 NOT_FOUND"
    source: [FR6.3, FR6.1]
  - id: BR4.4
    statement: "푸시가 실패해도 예약 상태는 되돌리지 않고 다른 구독에도 영향을 주지 않는다."
    category: constraint
    applies_to: Notification
    trigger: "구독 연결로 보내다 실패했을 때"
    logic: "IF 한 연결에 쓰기가 실패하거나 끊겼으면 THEN 그 구독만 제거하고 오류를 로그에 남긴다. 예약 상태 변경과 다른 연결의 푸시는 그대로 진행한다. 화면은 재연결하거나 현재 예약 조회로 상태를 복원할 수 있다."
    violation: "해당 없음"
    source: [FR6.3]
  - id: BR4.5
    statement: "구독 연결이 프록시나 브라우저에서 유휴로 끊기지 않도록 주기적으로 연결 유지 신호를 보낸다."
    category: constraint
    applies_to: 구독 연결
    trigger: "연결이 열려 있고 15초 동안 보낸 이벤트가 없을 때"
    logic: "SSE 주석 줄(': keep-alive')을 보낸다. 이 신호는 이벤트가 아니므로 화면이 처리하지 않는다."
    violation: "해당 없음"
    source: [FR6.1]
  - id: BR4.6
    statement: "웹훅 수신부터 화면 표시까지 1초 예산 가운데 이 서비스 몫은 결과 반영 요청을 받은 뒤 푸시를 연결에 쓰기까지 150ms이다."
    category: constraint
    applies_to: applyPaymentResult와 푸시
    trigger: "결과 반영 요청을 받은 시점부터 스냅샷이 구독 연결에 쓰이는 시점까지"
    logic: "로컬 환경에서 100건 반복 시험의 최댓값이 150ms 이내여야 한다. 전체 예산은 u3 웹훅 처리 150ms, 이 서비스 150ms, 프록시와 화면 200ms, 여유 500ms이다. 구간 값은 계약 C3의 가정을 시험 기준으로 확정한 것이며 사용자 확인 전까지 가정이다."
    violation: "시험 실패"
    source: [NFR1]
  - id: BR5.1
    statement: "모든 시각은 주입할 수 있는 시계에서 읽고 마감 타이머도 같은 시계를 쓴다."
    category: constraint
    applies_to: paymentRequestedAt, 마감, 취소 시각
    trigger: "시각을 읽거나 타이머를 예약할 때"
    logic: "시험에서 시계를 앞으로 돌려 179초, 180초, 181초 경계를 실제 대기 없이 검증할 수 있어야 한다. 시계는 UTC 시각을 돌려주고 시각은 ISO-8601 문자열로 표현한다."
    violation: "해당 없음"
    source: [NFR2, NFR7]
  - id: BR5.2
    statement: "같은 예약에 대한 상태 판정은 한 번에 하나씩 순서대로 처리하고, 같은 세션의 예약 생성도 한 번에 하나씩 처리한다."
    category: constraint
    applies_to: applyPaymentResult, 자동 취소, 예약 생성
    trigger: "같은 예약 또는 같은 세션에 요청이 동시에 올 때"
    logic: "예약 단위로 결과 반영·자동 취소·마감 확인이 서로 끼어들지 않는다. 세션 단위로 BR1.2, BR1.3의 검사와 저장이 끼어들지 않는다. 서로 다른 예약·세션은 서로를 막지 않는다. 같은 승인 이벤트 N회가 동시에 와도 상태 변경은 정확히 1회다."
    violation: "시험 실패 (중복 확정이나 한 세션의 결제 대기 2건)"
    source: [NFR5, FR5.3]
  - id: BR5.3
    statement: "저장소는 이 서비스의 인메모리이며 서비스를 다시 시작하면 예약과 타이머가 사라진다."
    category: constraint
    applies_to: Reservation, AppliedPaymentEvent, LatestSnapshot
    trigger: "서비스 시작과 재시작"
    logic: "시작할 때 예약은 비어 있다. 재시작 뒤 이전 예약 ID를 조회하면 404 NOT_FOUND이고, 늦게 온 결제 결과도 404 NOT_FOUND다. 사라진 결제 대기 예약의 타이머를 복구하지 않는다."
    violation: "해당 없음"
    source: [NFR8]
```

## 요약

| 규칙 | 한 줄 요약 |
|---|---|
| BR1.1~BR1.5 | 예약 생성(요청 검증, 멱등 키, 세션당 결제 대기 1건, 금액은 u1이 확정, 결제 대기로 시작·마감 설정) |
| BR2.1~BR2.7 | 상태 전이(결제 결과 원자 반영, 늦은 결과 거부, 180초 경계, 자동 취소, 종료 상태 불변, 실패 뒤 새 예약, 재고 미차감) |
| BR3.1~BR3.3 | 조회(세션 필수, 세션 격리, 현재 예약) |
| BR4.1~BR4.6 | 알림(상태마다 스냅샷 푸시, 최신 스냅샷 보관, 구독 시 먼저 전송, 푸시 실패 격리, 연결 유지, 1초 예산 중 150ms) |
| BR5.1~BR5.3 | 시계 주입, 직렬화, 인메모리 저장 |

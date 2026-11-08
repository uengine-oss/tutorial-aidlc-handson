# u3-payment-service 비즈니스 규칙

## Sources

- [scope] Workflow-selected scope: classic
- [Q2] 모의 PG는 payment-service 안에 두되 승인·실패 결과를 자기 웹훅 엔드포인트에 HTTP로 비동기 호출 (units-generation Q2)
- [memory:M1] requirements.md(FR4, FR5, NFR1, NFR5, NFR6), stories.md(US3.1, US3.2, US4.1), components.md(Payment, ADR-002, ADR-003), contract-summary.md(C2, C5, C6), u2-reservation-service의 functional-design/rules.md와 code-generation/code-summary.md(구현된 C5 동작)

```yaml
rules:
  - id: BR1.1
    statement: "결제 요청은 형식이 맞아야 한다."
    category: validation
    applies_to: POST /api/payments
    trigger: "결제 요청을 받을 때"
    logic: "IF X-Session-Id가 없거나 UUID 형식이 아니면 THEN 400 MISSING_SESSION. IF passId 또는 idempotencyKey가 비었거나(공백만 포함) 100자를 넘거나, durationHours가 1~24의 정수가 아니거나, mockScenario가 APPROVE·DELAY·FAIL이 아니거나, mockDelaySeconds가 0~600의 정수가 아니면 THEN 400 INVALID_REQUEST. 아무것도 만들지 않고 u2도 부르지 않는다."
    violation: "400 MISSING_SESSION, 400 INVALID_REQUEST"
    source: [FR4.1, FR8.1]
  - id: BR1.2
    statement: "결제 요청은 먼저 예약 서비스에 대기 예약을 만들고, 그 결과로 결제 기록과 모의 PG 요청을 만든다."
    category: policy
    applies_to: POST /api/payments
    trigger: "BR1.1 통과 뒤"
    logic: "u2의 예약 생성(sessionId, passId, durationHours, idempotencyKey)을 한 번 호출한다(제한 시간 2초, 자동 재시도 없음). 201이면 새 Payment를 만들고(status REQUESTED, amount와 requestedAt은 u2 응답 값) 모의 PG에 결제를 요청한 뒤 202와 paymentId, reservationId, status PENDING_PAYMENT, paymentRequestedAt을 돌려준다. 이 서비스가 예약 상태를 먼저 읽고 판단하지 않는다. 판단은 u2만 한다."
    violation: "해당 없음"
    source: [FR4.2, ADR-002]
  - id: BR1.3
    statement: "같은 세션의 같은 멱등 키 재요청은 새 결제를 만들지 않고 같은 결제를 돌려준다. 예약이 이미 끝난 뒤의 재요청에는 모의 PG를 다시 부르지 않는다."
    category: policy
    applies_to: POST /api/payments
    trigger: "u2가 기존 예약을 돌려줄 때 (예약 생성 응답 200)"
    logic: "u2가 200으로 기존 예약을 돌려주면 새 Payment도 새 모의 PG 요청도 만들지 않는다. (sessionId, idempotencyKey)로 이미 만든 Payment가 있으면 그 paymentId를 쓴다. 없으면(앞선 호출이 예약 생성 직후 실패해 Payment를 못 만든 경우) 그 예약의 Payment를 이때 만들되 예약이 결제 대기(PENDING_PAYMENT)일 때만 모의 PG를 요청한다. 응답은 기존 paymentId, reservationId, u2가 돌려준 현재 status, paymentRequestedAt이다. status가 PENDING_PAYMENT이면 202, 아니면 200이다."
    violation: "해당 없음"
    source: [FR4.2]
  - id: BR1.4
    statement: "예약 서비스가 거절한 결제 요청은 그 사유를 그대로 전하고 아무것도 남기지 않는다."
    category: policy
    applies_to: POST /api/payments
    trigger: "u2의 예약 생성이 오류를 돌려줄 때"
    logic: "u2의 404 PASS_NOT_FOUND는 404, 409 PENDING_EXISTS는 409(본문에 기존 reservationId를 담는다), 422 DURATION_NOT_AVAILABLE은 422로 같은 오류 코드와 함께 전한다. u2의 400, 502, 그 밖의 오류, 응답 시간 초과, 연결 실패는 502 UPSTREAM_FAILED로 바꾼다. 어느 경우에도 Payment와 모의 PG 요청을 만들지 않는다. 이미 u2에 예약이 만들어졌을 수 있으나(시간 초과) 같은 키로 다시 요청하면 BR1.3으로 이어 간다."
    violation: "404, 409, 422, 502"
    source: [FR4.2, FR8.1]
  - id: BR1.5
    statement: "결제 실패한 뒤에도 같은 세션이 다른 후보로 새 결제를 요청할 수 있다."
    category: policy
    applies_to: POST /api/payments
    trigger: "세션에 결제 실패 예약이 있고 새 결제 요청이 올 때"
    logic: "이 서비스는 이전 결제의 상태를 보고 새 요청을 막지 않는다. 새 idempotencyKey로 오면 u2가 새 예약을 만들면(BR1.2) 새 Payment와 새 모의 PG 요청이 만들어진다. 결제 대기 예약이 이미 있는 경우의 거절은 u2가 한다(BR1.4의 409)."
    violation: "해당 없음"
    source: [FR5.5]
  - id: BR2.1
    statement: "모의 PG는 시나리오에 따라 결과와 지연을 정하고 결과를 이 서비스의 웹훅 엔드포인트에 비동기 HTTP로 알린다."
    category: policy
    applies_to: 모의 PG 구성 요소
    trigger: "새 Payment에 모의 PG를 요청할 때"
    logic: "APPROVE: 곧바로(수십 ms 안에) 결과 APPROVED. DELAY: mockDelaySeconds 뒤에 결과 APPROVED(mockDelaySeconds가 0이면 200초). FAIL: 곧바로 결과 FAILED. 알림은 웹훅 호출 값(eventId는 새 UUID, paymentId, outcome, occurredAt)을 JSON으로 만들어 본문 원문 바이트에 대한 HMAC-SHA256 서명을 X-Signature(sha256=<소문자 hex>)로 붙여 이 서비스의 웹훅 주소로 보낸다. 결제 요청 응답(202)을 돌려준 뒤에 일어나며 요청 처리를 막지 않는다. 한 Payment에 알림은 한 번만 보낸다. 웹훅이 502나 오류를 돌려줘도 다시 보내지 않는다."
    violation: "해당 없음"
    source: [FR5.1, FR5.5]
  - id: BR2.2
    statement: "모의 PG 구성 요소와 웹훅 수신부는 코드 경계를 나눠, 실제 PG로 바꿀 때 모의 PG 부분만 걷어낼 수 있다."
    category: constraint
    applies_to: 패키지 구조
    trigger: "구현할 때"
    logic: "모의 PG는 `결제 요청을 받는다`는 인터페이스 하나 뒤에 둔다. 웹훅 수신부와 Payment 로직은 모의 PG 패키지를 참조하지 않는다. mockScenario·mockDelaySeconds는 모의 PG만 쓰며 실제 PG 연동 때는 무시한다."
    violation: "해당 없음 (구조 시험으로 확인)"
    source: [ADR-005, FR5.5]
  - id: BR3.1
    statement: "웹훅은 출처와 무관하게 서명을 검증하고, 맞지 않으면 거부하며 상태를 바꾸지 않고 기록한다."
    category: authorization
    applies_to: POST /api/payments/webhook
    trigger: "웹훅을 받을 때"
    logic: "본문 원문 바이트에 대한 HMAC-SHA256(키는 환경 변수 PAYMENT_WEBHOOK_SECRET)을 계산해 X-Signature의 값(`sha256=` 뒤 소문자 hex)과 상수 시간으로 비교한다. 헤더가 없거나 형식이 다르거나 값이 다르면 401 INVALID_SIGNATURE를 돌려준다. 이때 Payment, WebhookEvent, 예약을 바꾸지 않고, 거부 사실(시각, 본문 길이, 이유)을 로그에 남긴다. 서명 검증은 본문을 해석하기 전에 한다. 거부 로그에 본문 내용과 서명 값을 남기지 않는다."
    violation: "401 INVALID_SIGNATURE"
    source: [FR5.1, NFR6, AC4.1.3]
  - id: BR3.2
    statement: "공유 비밀 값은 환경 변수로 주입하고 없으면 서비스가 기동하지 않는다."
    category: constraint
    applies_to: 서비스 시작
    trigger: "서비스를 시작할 때"
    logic: "PAYMENT_WEBHOOK_SECRET이 없거나 비어 있으면 기동을 거부한다(빨리 실패). 코드와 설정 파일에는 비밀 값이 없다. 모의 PG도 같은 환경 변수의 값으로 서명한다."
    violation: "기동 거부"
    source: [NFR6]
  - id: BR3.3
    statement: "서명이 맞는 웹훅은 형식을 검증하고 알려진 결제에 대한 것인지 확인한다."
    category: validation
    applies_to: POST /api/payments/webhook
    trigger: "BR3.1 통과 뒤"
    logic: "본문이 JSON이 아니거나 eventId(1~100자), paymentId, outcome(APPROVED·FAILED), occurredAt(날짜 시각)이 맞지 않으면 400 INVALID_REQUEST. paymentId에 해당하는 Payment가 없으면 404 UNKNOWN_PAYMENT. 어느 경우에도 상태를 바꾸지 않는다."
    violation: "400 INVALID_REQUEST, 404 UNKNOWN_PAYMENT"
    source: [FR5.2]
  - id: BR3.4
    statement: "이미 처리한 이벤트 ID가 다시 오면 예약 서비스를 다시 부르지 않고 중복으로 알린다."
    category: policy
    applies_to: POST /api/payments/webhook
    trigger: "BR3.3 통과 뒤"
    logic: "IF eventId로 기록한 WebhookEvent가 있으면 THEN 200 { handled: DUPLICATE }를 돌려주고 u2를 부르지 않으며 Payment도 바꾸지 않는다. 같은 eventId의 웹훅이 동시에 여러 개 와도 같은 eventId의 처리(이벤트 조회, u2 호출, 기록)는 한 번에 하나씩 순서대로 한다. 따라서 예약 확정은 정확히 한 번 일어난다."
    violation: "해당 없음"
    source: [FR5.3, NFR5, AC4.1.2]
  - id: BR3.5
    statement: "새 이벤트의 결과는 예약 서비스의 원자 연산으로만 반영하고, 돌려받은 결과가 거부이면 무시했다고 기록한다."
    category: policy
    applies_to: POST /api/payments/webhook
    trigger: "BR3.4에서 새 이벤트일 때"
    logic: "u2의 결제 결과 반영(reservationId, eventId, outcome)을 호출한다(제한 시간 2초, 200ms 간격 최대 2회 재시도 — 같은 eventId라 안전하다). 이 서비스는 예약 상태를 먼저 읽지 않는다. 응답 result가 APPLIED이면 WebhookEvent(handled APPLIED)를 기록하고 Payment.status를 outcome에 맞춰 APPROVED 또는 FAILED로 바꾸고 200 { handled: APPLIED }. result가 REJECTED이면 WebhookEvent(handled IGNORED)를 기록하고 Payment.status를 IGNORED로 바꾸고 무시한 사실(결제, 예약, eventId, outcome, 예약 현재 상태)을 로그에 남기고 200 { handled: IGNORED }. 승인이 되살아나지 않는 것은 u2가 보장한다."
    violation: "해당 없음"
    source: [FR5.2, FR5.4, AC4.1.5]
  - id: BR3.6
    statement: "예약 반영에 실패한 이벤트는 처리한 것으로 기록하지 않고 502로 알린다."
    category: policy
    applies_to: POST /api/payments/webhook
    trigger: "BR3.5의 u2 호출이 재시도 후에도 실패하거나 오류를 돌려줄 때"
    logic: "u2가 응답하지 못하거나 404·400·502·그 밖의 오류를 돌려주면 502 UPSTREAM_FAILED를 돌려준다. WebhookEvent를 기록하지 않고 Payment.status도 바꾸지 않는다. 같은 이벤트가 다시 오면 처음부터 처리한다. 모의 PG는 502를 받아도 다시 보내지 않으므로 이 승인은 유실될 수 있고, 그때 예약은 결제 요청 시점부터 180초 뒤 자동 취소된다. 이 결과를 정상 동작으로 본다."
    violation: "502 UPSTREAM_FAILED"
    source: [FR5.2, FR7.1]
  - id: BR3.7
    statement: "웹훅 수신부터 예약 서비스 반영 완료까지 150ms 이내다."
    category: constraint
    applies_to: 웹훅 처리
    trigger: "새 이벤트일 때"
    logic: "서명 검증, 형식 검증, 중복 조회, u2 호출, 기록을 모두 합쳐 로컬 환경에서 웹훅 요청을 받은 시점부터 응답을 돌려줄 때까지 150ms 이내여야 한다(u2가 정상 응답하는 경우). 1초 예산의 구간 값은 계약 C3의 가정을 따른다. 시험에서 측정한다."
    violation: "시험 실패"
    source: [NFR1]
  - id: BR4.1
    statement: "결제 요청에서 사용자 입력을 신뢰하지 않는 값은 예약 서비스가 확정한다."
    category: calculation
    applies_to: Payment.amount
    trigger: "예약 생성 응답을 받을 때"
    logic: "amount는 u2의 응답 값만 쓴다. 클라이언트가 금액을 보내지 않으며 보내도 무시한다. durationHours는 C1 응답의 값을 그대로 받아 u2에 넘긴다."
    violation: "해당 없음"
    source: [FR4.1]
  - id: BR5.1
    statement: "저장소는 이 서비스의 인메모리이며 서비스를 다시 시작하면 결제 기록과 처리한 이벤트가 사라진다."
    category: constraint
    applies_to: Payment, WebhookEvent, PgRequest
    trigger: "서비스 시작과 재시작"
    logic: "시작할 때 기록은 비어 있다. 재시작 전에 보낸 모의 PG 알림 가운데 아직 예약된 것은 사라진다. 재시작 뒤 도착한 웹훅은 paymentId를 모르므로 404 UNKNOWN_PAYMENT다. 예약은 u2의 타이머가 자동 취소한다(u2가 살아 있을 때)."
    violation: "해당 없음"
    source: [NFR8]
```

## 요약

| 규칙 | 한 줄 요약 |
|---|---|
| BR1.1~BR1.5 | 결제 요청(형식 검증, u2에 대기 예약을 먼저 만들고 결제 기록과 모의 PG 요청, 멱등 재요청, u2 거절 전달, 실패 뒤 새 결제) |
| BR2.1~BR2.2 | 모의 PG(시나리오별 비동기 서명 웹훅, 코드 경계) |
| BR3.1~BR3.7 | 웹훅(서명 검증, 비밀 값 환경 변수, 형식·알려진 결제, 이벤트 중복, u2 원자 반영과 무시 기록, 반영 실패 502, 150ms) |
| BR4.1 | 금액은 u2가 확정 |
| BR5.1 | 인메모리 저장 |

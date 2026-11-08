# u3-payment-service 엔티티

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] unit-of-work.md(U3), components.md(Payment의 Payment, WebhookEvent), contract-summary.md(C2, C5, C6), requirements.md(FR4, FR5, NFR5, NFR6), stories.md(US3.1, US3.2, US4.1), u2-reservation-service/code-generation/code-summary.md(구현된 C5 동작과 계약 확장)

이 서비스는 결제 요청 기록, 모의 PG에 보낸 요청, 처리한 웹훅 이벤트를 소유한다. 예약 상태는 u2-reservation-service가 소유하며 이 서비스는 읽지 않는다. 저장소는 이 서비스 안의 인메모리 저장소이며 다른 서비스와 공유하지 않는다.

```yaml
entities:
  - name: Payment
    description: 선택한 주차권 한 건에 대한 결제 요청 기록. 예약 서비스가 만든 대기 예약 하나에 속하며, 모의 PG가 알려 준 결과(요청됨, 승인, 실패, 무시됨)를 기록한다. 사용자에게 보이는 예약 상태의 기준은 Reservation.status이며 이 값이 아니다.
    attributes:
      - { name: paymentId, type: string, required: true, unique: true, constraints: "서버가 만든 UUID 문자열" }
      - { name: reservationId, type: string, required: true, unique: true, references: "Reservation.reservationId (u2-reservation-service 소유)" }
      - { name: sessionId, type: string, required: true, constraints: "화면이 만든 UUID (X-Session-Id)" }
      - { name: idempotencyKey, type: string, required: true, constraints: "(sessionId, idempotencyKey) 쌍이 유일하다. 1~100자" }
      - { name: passId, type: string, required: true, constraints: "1~100자" }
      - { name: durationHours, type: integer, required: true, min: 1, max: 24 }
      - { name: amount, type: integer, required: true, min: 0, constraints: "u2가 예약 생성 때 돌려준 금액(원). 생성 뒤 바뀌지 않는다" }
      - { name: mockScenario, type: string, required: true, allowed_values: [APPROVE, DELAY, FAIL], default: APPROVE }
      - { name: mockDelaySeconds, type: integer, required: true, min: 0, max: 600, default: 0 }
      - { name: status, type: string, required: true, allowed_values: [REQUESTED, APPROVED, FAILED, IGNORED], default: REQUESTED }
      - { name: requestedAt, type: timestamp, required: true, constraints: "u2가 돌려준 paymentRequestedAt" }
    constraints:
      - "paymentId는 전체에서 유일하다"
      - "(sessionId, idempotencyKey)는 유일하다 (u2도 같은 쌍으로 예약을 하나만 만든다)"
      - "status는 REQUESTED에서 시작해 APPROVED, FAILED, IGNORED 중 하나로 한 번만 바뀐다. 이후에는 바뀌지 않는다"
    relationships:
      - { to: WebhookEvent, cardinality: "0..*", direction: "Payment -> WebhookEvent", note: "한 결제에 대한 통지. 결제가 끝나기 전에는 0건이다" }

  - name: WebhookEvent
    description: 서명 검증을 통과해 처리한 웹훅 이벤트 한 건. 같은 eventId를 다시 처리하지 않기 위한 기록이다. 예약 서비스에 결과를 반영하는 데 성공한 뒤에만 남긴다.
    attributes:
      - { name: eventId, type: string, required: true, unique: true, constraints: "1~100자. 전체에서 유일하다" }
      - { name: paymentId, type: string, required: true, references: Payment.paymentId }
      - { name: outcome, type: string, required: true, allowed_values: [APPROVED, FAILED] }
      - { name: receivedAt, type: timestamp, required: true }
      - { name: handled, type: string, required: true, allowed_values: [APPLIED, IGNORED], constraints: "u2가 APPLIED를 돌려주면 APPLIED, REJECTED를 돌려주면 IGNORED" }
    constraints:
      - "eventId는 유일하다. 같은 eventId가 다시 오면 새 기록을 만들지 않는다"
      - "u2 반영에 실패한 이벤트는 기록하지 않는다"

  - name: PgRequest
    description: 모의 PG에 보낸 결제 요청 한 건. 모의 PG 구성 요소 안에서만 쓰며, 실제 PG로 바꿀 때 이 엔티티와 모의 PG 구성 요소를 함께 걷어낸다.
    attributes:
      - { name: paymentId, type: string, required: true, references: Payment.paymentId }
      - { name: scenario, type: string, required: true, allowed_values: [APPROVE, DELAY, FAIL] }
      - { name: delaySeconds, type: integer, required: true, min: 0, max: 600 }
      - { name: outcome, type: string, required: true, allowed_values: [APPROVED, FAILED], constraints: "APPROVE와 DELAY는 APPROVED, FAIL은 FAILED" }
    constraints:
      - "한 결제에 모의 PG 요청은 한 번만 한다"

  - name: WebhookCall
    description: 모의 PG가 이 서비스의 웹훅 엔드포인트에 보내는 호출 값. 저장하지 않고 요청 처리 중에만 존재한다.
    attributes:
      - { name: eventId, type: string, required: true, constraints: "모의 PG가 만든 UUID" }
      - { name: paymentId, type: string, required: true }
      - { name: outcome, type: string, required: true, allowed_values: [APPROVED, FAILED] }
      - { name: occurredAt, type: timestamp, required: true }
      - { name: signature, type: string, required: true, constraints: "X-Signature 헤더. sha256=<소문자 hex>" }
```

## 요약

| 엔티티 | 저장 | 소유 컴포넌트 | 비고 |
|---|---|---|---|
| Payment | 인메모리 | Payment | PG 결과 기록. 예약 상태의 기준이 아님 |
| WebhookEvent | 인메모리 | Payment | 처리한 이벤트 ID. u2 반영 성공 뒤에만 기록 |
| PgRequest | 인메모리 (모의 PG 안) | 모의 PG (외부 의존성으로 취급) | 실제 PG 전환 때 걷어냄 |
| WebhookCall | 저장 안 함 | 요청 처리 중 | 값 객체 |

## components.md와 달라진 점

- `Payment`에 `sessionId`, `idempotencyKey`, `passId`, `durationHours`, `amount`, `mockScenario`, `mockDelaySeconds`, `requestedAt`을 더했다. 모두 계약 C2·C5가 요구하는 값이다.
- `WebhookEvent`의 `ignored`(boolean)를 `handled`(APPLIED 또는 IGNORED)로 바꾸고 `Payment.status`에 IGNORED가 있는 것과 맞췄다. 의미는 같다.
- `PgRequest`는 components.md에 없다. ADR-005가 모의 PG를 외부 의존성으로 보므로, 이 서비스 안의 모의 PG 구성 요소가 가진 요청 기록으로 따로 두었다.

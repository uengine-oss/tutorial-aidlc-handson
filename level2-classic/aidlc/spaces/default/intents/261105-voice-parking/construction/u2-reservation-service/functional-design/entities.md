# u2-reservation-service 엔티티

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] unit-of-work.md(U2), components.md(Reservation의 Reservation, Notification의 Subscription), contract-summary.md(C3, C4, C5), requirements.md(FR4~FR8), stories.md(US3.1~US4.2)

이 서비스는 예약 상태와 푸시용 최신 스냅샷만 소유한다. 주차장·주차권 데이터는 u1-search-service가 소유하므로 여기에는 `passId`와 조회 시점의 `lotName`, `amount`만 사본으로 저장한다. 결제 상태(Payment)와 웹훅 이벤트는 u3-payment-service가 소유한다. 저장소는 이 서비스 안의 인메모리 저장소이며 다른 서비스와 공유하지 않는다.

```yaml
entities:
  - name: Reservation
    description: 한 세션이 고른 주차권 한 건의 예약. 상태의 단일 확정 지점이며 결제 대기에서 시작해 정확히 하나의 종료 상태로 끝난다.
    attributes:
      - { name: reservationId, type: string, required: true, unique: true, constraints: "서버가 만든 UUID 문자열" }
      - { name: sessionId, type: string, required: true, constraints: "화면이 만든 UUID (X-Session-Id). 로그인이 없으므로 소유자 식별은 이 값뿐이다" }
      - { name: idempotencyKey, type: string, required: true, constraints: "(sessionId, idempotencyKey) 쌍이 유일하다" }
      - { name: passId, type: string, required: true, references: "ParkingPass.passId (u1-search-service 소유, 값만 보관)" }
      - { name: lotName, type: string, required: true, constraints: "예약 생성 때 u1에서 받은 주차장 이름 사본" }
      - { name: durationHours, type: integer, required: true, min: 1 }
      - { name: amount, type: integer, required: true, min: 0, constraints: "u1이 돌려준 price (요청 이용 시간 전체의 합계 금액, 원). 생성 뒤 바뀌지 않는다" }
      - { name: status, type: string, required: true, allowed_values: [PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, CANCELLED], default: PENDING_PAYMENT }
      - { name: version, type: integer, required: true, min: 1, default: 1, constraints: "상태가 바뀔 때마다 정확히 1 증가한다. 바뀌지 않으면 증가하지 않는다" }
      - { name: paymentRequestedAt, type: timestamp, required: true, constraints: "예약 생성을 기록한 시각(UTC). 180초 마감의 기준이며 생성 뒤 바뀌지 않는다" }
      - { name: confirmedAt, type: timestamp, required: false, constraints: "status가 CONFIRMED일 때만 있다" }
      - { name: failedAt, type: timestamp, required: false, constraints: "status가 PAYMENT_FAILED일 때만 있다 (내부 속성, 응답에는 싣지 않는다)" }
      - { name: cancelledAt, type: timestamp, required: false, constraints: "status가 CANCELLED일 때만 있다" }
      - { name: message, type: string, required: true, constraints: "상태별 화면 안내 문구 (functional-spec.md의 상태별 안내 표)" }
    constraints:
      - "reservationId는 전체에서 유일하다"
      - "(sessionId, idempotencyKey)는 유일하다"
      - "한 sessionId에 status가 PENDING_PAYMENT인 예약은 동시에 최대 1건이다"
      - "status가 CONFIRMED, PAYMENT_FAILED, CANCELLED이면 이후 status와 version이 바뀌지 않는다"
      - "confirmedAt, failedAt, cancelledAt 중 최대 하나만 값을 가진다"
    relationships:
      - { to: AppliedPaymentEvent, cardinality: "1..*", direction: "Reservation -> AppliedPaymentEvent", note: "예약에 반영(또는 거부)을 시도한 결제 결과 기록. 0건일 수 있다" }
      - { to: LatestSnapshot, cardinality: "1..1", direction: "Reservation -> LatestSnapshot", note: "상태가 바뀔 때마다 Notification이 보관하는 최신 사본" }

  - name: AppliedPaymentEvent
    description: 결제 결과 반영 연산(applyPaymentResult)이 한 번 판정한 이벤트와 그 판정. 같은 eventId가 다시 오면 처음 낸 판정을 그대로 돌려주기 위한 기록이다.
    attributes:
      - { name: reservationId, type: string, required: true, references: Reservation.reservationId }
      - { name: eventId, type: string, required: true, constraints: "(reservationId, eventId) 쌍이 유일하다" }
      - { name: outcome, type: string, required: true, allowed_values: [APPROVED, FAILED] }
      - { name: result, type: string, required: true, allowed_values: [APPLIED, REJECTED], constraints: "처음 낸 판정" }
      - { name: reservationStatus, type: string, required: true, allowed_values: [PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, CANCELLED], constraints: "판정을 내린 직후의 예약 상태" }
      - { name: decidedAt, type: timestamp, required: true }
    constraints:
      - "(reservationId, eventId)는 유일하다"
      - "한 번 기록한 result와 reservationStatus는 바뀌지 않는다"

  - name: LatestSnapshot
    description: Notification이 예약마다 보관하는 가장 최근 상태 스냅샷. 푸시와 재구독 때 현재 상태를 한 번 먼저 보내는 데 쓴다. 상태 자체의 기준은 Reservation이다.
    attributes:
      - { name: reservationId, type: string, required: true, unique: true, references: Reservation.reservationId }
      - { name: sessionId, type: string, required: true }
      - { name: snapshot, type: object, required: true, constraints: "계약 C3의 Reservation 응답 형식과 같은 값(reservationId, version, status, lotName, durationHours, amount, paymentRequestedAt, confirmedAt, cancelledAt, message)" }
    constraints:
      - "보관 중인 snapshot.version보다 높은 version만 덮어쓴다"

  - name: Subscription
    description: 한 화면이 한 예약의 상태 변화를 SSE로 받고 있는 연결. 연결이 끊기면 사라지고 재연결하면 새로 만들어진다.
    attributes:
      - { name: subscriptionId, type: string, required: true, unique: true }
      - { name: sessionId, type: string, required: true }
      - { name: reservationId, type: string, required: true, references: Reservation.reservationId }
      - { name: connectedAt, type: timestamp, required: true }
    constraints:
      - "같은 reservationId에 연결이 동시에 여러 개 있을 수 있다 (탭 여러 개, 재연결 겹침)"
      - "Subscription의 sessionId는 구독한 예약의 sessionId와 같다"

  - name: PaymentResultCommand
    description: 결제 결과 반영 요청 값. 저장하지 않고 요청 처리 중에만 존재한다.
    attributes:
      - { name: reservationId, type: string, required: true }
      - { name: eventId, type: string, required: true, constraints: "1~100자" }
      - { name: outcome, type: string, required: true, allowed_values: [APPROVED, FAILED] }
```

## 요약

| 엔티티 | 저장 | 소유 컴포넌트 | 비고 |
|---|---|---|---|
| Reservation | 인메모리 | Reservation | 상태의 유일한 기준. 종료 상태는 불변 |
| AppliedPaymentEvent | 인메모리 | Reservation | 멱등 판정 기록. 예약과 함께 보관 |
| LatestSnapshot | 인메모리 | Notification | 예약당 1건, version이 높을 때만 갱신 |
| Subscription | 인메모리 (연결 수명) | Notification | 연결이 끊기면 제거 |
| PaymentResultCommand | 저장 안 함 | 요청 처리 중 | 값 객체 |

## components.md와 달라진 점

- components.md는 `lastSnapshot`을 Subscription의 속성으로 두었다. 연결은 끊기면 사라지는데 재구독 때 보내 줄 스냅샷은 연결보다 오래 살아야 하므로, 스냅샷을 예약 단위의 `LatestSnapshot`으로 분리하고 Subscription에서는 뺐다. 소유 컴포넌트(Notification)와 동작은 그대로다.
- `AppliedPaymentEvent`는 components.md에 없는 기록이다. 계약 C5가 "같은 eventId에는 처음 낸 결과를 그대로 돌려준다"고 요구하므로 Reservation이 판정 기록을 가져야 한다. Payment의 WebhookEvent와는 다른 기록이다(그쪽은 u3-payment-service가 이벤트 중복을 거르는 용도).
- `Reservation`에 `idempotencyKey`, `passId`, `lotName`, `durationHours`, `version`, `failedAt`, `message`를 더했다. 모두 계약 C3·C5가 요구하는 값이다.

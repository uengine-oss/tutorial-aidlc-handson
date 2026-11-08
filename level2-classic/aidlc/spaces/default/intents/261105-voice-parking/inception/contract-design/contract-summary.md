# Contract Summary

## Sources

- [desc] Initial description: 사용자가 음성 텍스트('강남역 근처 2시간 주차권 찾아줘')를 보내면 최적 주차권을 추천하고 결제와 예약을 처리하는 VoiceParking 프로토타입. 결제가 끝나면 사용자에게 바로 예약 완료를 보여 줘야 한다.
- [scope] Workflow-selected scope: classic
- [Q1] 세션은 `X-Session-Id` 헤더(화면이 만든 UUID)
- [Q2] 웹훅은 HMAC-SHA256 서명(`X-Signature`), 공유 비밀 값은 환경 변수, 이벤트 ID로 중복 차단
- [Q3] 호출 제한 시간 2초, 결제 결과 반영과 주차권 조회만 200ms 간격 최대 2회 재시도, 예약 생성은 재시도 없이 결제 요청에 멱등 키
- [memory:M1] unit-of-work.md(유닛 U1~U5), unit-of-work-dependency.md(연동 지점 표), components.md(applyPaymentResult, 스냅샷), requirements.md(FR5~FR7, NFR1, NFR5, NFR6)

## 공통 규칙

- 모든 연동은 HTTP/JSON이다. 포트: U1 8081, U2 8082, U3 8083, 화면(Vite) 5173. 화면은 Vite 프록시가 `/api/search` → U1, `/api/reservations` → U2, `/api/payments` → U3로 나눠 보낸다. 서비스 간 호출은 프록시를 거치지 않고 환경 변수로 주입한 주소로 직접 부른다.
- 세션 식별: 화면이 만든 UUID를 모든 요청에 `X-Session-Id` 헤더로 보낸다.
- 오류 본문은 모두 `{ "code": "<대문자 오류 코드>", "message": "<사람이 읽는 설명>" }` 형식이다.
- 시간은 UTC ISO-8601 문자열, 금액은 원 단위 정수, 이용 시간은 시간 단위 정수다.

## Contracts

| # | Provider Unit | Consumer | Mechanism | Owner |
|---|---|---|---|---|
| C1 | U1 u1-search-service | U4 u4-web-ui | 동기 REST (Vite 프록시) | U1 |
| C2 | U3 u3-payment-service | U4 u4-web-ui | 동기 REST (Vite 프록시) | U3 |
| C3 | U2 u2-reservation-service | U4 u4-web-ui | 동기 REST + SSE (Vite 프록시) | U2 |
| C4 | U1 u1-search-service | U2 u2-reservation-service | 동기 REST (직접 호출) | U1 |
| C5 | U2 u2-reservation-service | U3 u3-payment-service | 동기 REST (직접 호출) | U2 |
| C6 | U3 u3-payment-service (웹훅 수신) | U3 모의 PG | 비동기 HTTP 웹훅 (서명) | U3 |

시스템 밖 소비자가 쓰는 공개 API는 없다.

## C1 추천 요청 (U4 → U1)

```yaml
openapi: 3.0.3
info: { title: search-service, version: "1" }
paths:
  /api/search/recommendations:
    post:
      summary: 음성 텍스트로 주차권 추천을 요청한다
      parameters:
        - { name: X-Session-Id, in: header, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [text]
              properties:
                text: { type: string, minLength: 1, maxLength: 200 }
      responses:
        "200":
          description: 추천 결과. 후보가 없으면 recommendations가 빈 배열이다.
          content:
            application/json:
              schema:
                type: object
                required: [query, recommendations]
                properties:
                  query:
                    type: object
                    required: [place, durationHours]
                    properties:
                      place: { type: string }
                      durationHours: { type: integer, minimum: 1 }
                  recommendations:
                    type: array
                    maxItems: 3
                    items: { $ref: "#/components/schemas/Recommendation" }
        "422":
          description: 장소 또는 이용 시간을 해석하지 못함
          content:
            application/json:
              schema: { $ref: "#/components/schemas/Error" }
              examples:
                missingPlace: { value: { code: MISSING_PLACE, message: "장소를 알려 주세요" } }
                missingDuration: { value: { code: MISSING_DURATION, message: "이용 시간을 알려 주세요" } }
components:
  schemas:
    Recommendation:
      type: object
      required: [passId, lotName, price, distanceMeters, score, rank, reason]
      properties:
        passId: { type: string }
        lotName: { type: string }
        price: { type: integer, description: "요청 이용 시간(query.durationHours) 전체에 대한 합계 금액(원)" }
        distanceMeters: { type: integer }
        score: { type: integer, minimum: 0, maximum: 100 }
        rank: { type: string, enum: [BEST, CANDIDATE] }
        reason:
          type: object
          required: [topFactor, text]
          properties:
            topFactor: { type: string, enum: [PRICE, DISTANCE, TIME_FIT] }
            text: { type: string }
    Error:
      type: object
      required: [code, message]
      properties:
        code: { type: string }
        message: { type: string }
```

- 추천 순서는 `BEST` 1개 다음에 `CANDIDATE`가 점수 순으로 온다. 종합 점수가 같으면 가격이 낮은 쪽이 앞선다.
- 소비자는 모르는 필드를 무시한다.

## C2 결제 요청 (U4 → U3)

```yaml
openapi: 3.0.3
info: { title: payment-service, version: "1" }
paths:
  /api/payments:
    post:
      summary: 선택한 주차권의 결제를 요청한다 (대기 예약 생성 + 모의 PG 요청)
      parameters:
        - { name: X-Session-Id, in: header, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [passId, durationHours, idempotencyKey]
              properties:
                passId: { type: string }
                durationHours: { type: integer, minimum: 1, description: "C1 응답의 query.durationHours를 그대로 보낸다. 이용 시간의 유일한 출처" }
                idempotencyKey: { type: string, description: "같은 값의 재요청은 새 결제를 만들지 않고 기존 결과를 돌려준다. U3는 이 값으로 결제를 하나만 만들고 C5 예약 생성에도 같은 값을 넘긴다" }
                mockScenario:
                  type: string
                  enum: [APPROVE, DELAY, FAIL]
                  default: APPROVE
                  description: "모의 PG 시나리오. 실제 PG 연동 시 무시된다"
                mockDelaySeconds: { type: integer, minimum: 0, maximum: 600, default: 0 }
      responses:
        "202":
          description: 결제 요청이 접수됨. 결과는 SSE로 온다.
          content:
            application/json:
              schema:
                type: object
                required: [paymentId, reservationId, status, paymentRequestedAt]
                properties:
                  paymentId: { type: string }
                  reservationId: { type: string }
                  status: { type: string, enum: [PENDING_PAYMENT] }
                  paymentRequestedAt: { type: string, format: date-time }
        "404": { description: "PASS_NOT_FOUND" }
        "409": { description: "PENDING_EXISTS: 이 세션에 다른 키의 결제 대기 예약이 이미 있음. 본문에 기존 reservationId를 담는다" }
        "422": { description: "DURATION_NOT_AVAILABLE: 이 주차권이 요청한 이용 시간을 제공하지 못함" }
        "502": { description: "UPSTREAM_FAILED: 예약 서비스 또는 검색 서비스 호출 실패" }
```

- 모든 오류 본문은 공통 오류 형식을 따른다. `PENDING_EXISTS` 본문은 오류 형식에 `reservationId`를 더한 것이다.
- 같은 `idempotencyKey`의 재요청은 같은 `paymentId`와 `reservationId`를 돌려준다. 예약 생성은 시간 초과로 실패했는데 서비스 U2에는 이미 예약이 있는 경우에도, 사용자가 같은 키로 다시 보내면 같은 예약을 돌려받아 결제를 이어 갈 수 있다.
- 금액(`amount`)은 C4가 돌려준 `price`(요청 이용 시간 전체의 합계 금액)이며 U2가 예약 생성 때 저장한다.

## C3 예약 조회와 실시간 알림 (U4 → U2)

```yaml
openapi: 3.0.3
info: { title: reservation-service, version: "1" }
paths:
  /api/reservations/current:
    get:
      summary: 이 세션의 가장 최근 예약 상태를 돌려준다 (새로 고침, 재구독 전 복원용)
      parameters:
        - { name: X-Session-Id, in: header, required: true, schema: { type: string, format: uuid } }
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Reservation" } } } }
        "404": { description: "NO_RESERVATION" }
  /api/reservations/{reservationId}:
    get:
      summary: 예약 한 건의 현재 상태. 다른 세션의 예약이면 404를 돌려준다.
      parameters:
        - { name: reservationId, in: path, required: true, schema: { type: string } }
        - { name: X-Session-Id, in: header, required: true, schema: { type: string, format: uuid } }
      responses:
        "200": { content: { application/json: { schema: { $ref: "#/components/schemas/Reservation" } } } }
        "404": { description: "NOT_FOUND" }
  /api/reservations/{reservationId}/events:
    get:
      summary: 예약 상태 변화를 SSE로 받는다. 연결하면 먼저 보관 중인 최근 스냅샷을 한 번 보낸다.
      description: "브라우저 EventSource는 사용자 지정 헤더를 보낼 수 없으므로 이 엔드포인트만 세션을 sessionId 쿼리 파라미터로 받는다. 헤더가 함께 오면 헤더가 우선한다."
      parameters:
        - { name: reservationId, in: path, required: true, schema: { type: string } }
        - { name: sessionId, in: query, required: false, schema: { type: string, format: uuid }, description: "X-Session-Id 헤더 대신 쓰는 같은 값" }
        - { name: X-Session-Id, in: header, required: false, schema: { type: string, format: uuid } }
      responses:
        "200":
          description: "text/event-stream. 이벤트 이름은 snapshot, id는 Reservation의 version, 데이터는 Reservation JSON"
        "401": { description: "MISSING_SESSION: 헤더와 쿼리 파라미터 둘 다 없음" }
        "404": { description: "NOT_FOUND" }
components:
  schemas:
    Reservation:
      type: object
      required: [reservationId, version, status, lotName, durationHours, amount, paymentRequestedAt]
      properties:
        reservationId: { type: string }
        version: { type: integer, description: "상태가 바뀔 때마다 1씩 증가. 더 낮은 version의 스냅샷은 버린다" }
        status: { type: string, enum: [PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, CANCELLED] }
        lotName: { type: string }
        durationHours: { type: integer }
        amount: { type: integer }
        paymentRequestedAt: { type: string, format: date-time }
        confirmedAt: { type: string, format: date-time }
        cancelledAt: { type: string, format: date-time }
        message: { type: string, description: "화면에 그대로 보일 안내. 예: 승인 지연으로 예약이 취소되었습니다" }
```

- 푸시 이벤트의 데이터는 `Reservation`이며 `version`이 낮은 것은 화면이 버린다. 이로써 늦게 도착한 오래된 스냅샷이 덮어쓰는 것을 막는다.
- 이 엔드포인트의 세션은 URL에 실리므로 서버 로그에 남을 수 있다. 세션 ID는 추측하기 어려운 UUID이고 시스템이 로컬에서만 동작해 프로토타입에서는 받아들인다. [assumption]
- 브라우저는 연결이 끊기면 같은 URL로 다시 연결하며(`sessionId` 포함), 서버는 연결할 때마다 최근 스냅샷을 먼저 보낸다.
- 웹훅 수신 시각부터 화면 표시까지 1초 이내라는 NFR1 예산은 구간별로 U3 웹훅 처리 150ms, U2 결과 반영과 푸시 150ms, 프록시와 화면 표시 200ms, 여유 500ms로 나눈다. [assumption] 구간 값은 사용자 확인 전 가정이며 Functional Design에서 시험 기준으로 확정한다.

## C4 주차권 조회 (U2 → U1)

```yaml
openapi: 3.0.3
info: { title: search-service internal, version: "1" }
paths:
  /internal/passes/{passId}:
    get:
      summary: 요청 이용 시간 기준으로 예약 생성에 필요한 주차권 가격과 주차장 정보를 돌려준다
      parameters:
        - { name: passId, in: path, required: true, schema: { type: string } }
        - { name: durationHours, in: query, required: true, schema: { type: integer, minimum: 1 } }
      responses:
        "200":
          description: 주차권 정보. price는 durationHours 전체에 대한 합계 금액이다.
          content:
            application/json:
              schema:
                type: object
                required: [passId, lotName, durationHours, price]
                properties:
                  passId: { type: string }
                  lotName: { type: string }
                  durationHours: { type: integer }
                  price: { type: integer }
        "404": { description: "PASS_NOT_FOUND" }
        "422": { description: "DURATION_NOT_AVAILABLE: 이 주차권이 요청 이용 시간을 포함하지 못함" }
```

- 읽기 전용이므로 제한 시간 2초, 200ms 간격 최대 2회 재시도를 적용한다.

## C5 예약 생성과 결제 결과 반영 (U3 → U2)

```yaml
openapi: 3.0.3
info: { title: reservation-service internal, version: "1" }
paths:
  /internal/reservations:
    post:
      summary: 결제 대기 예약을 만든다. 자동 재시도는 하지 않지만 멱등 키로 같은 요청을 다시 보내도 안전하다.
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [sessionId, passId, durationHours, idempotencyKey]
              properties:
                sessionId: { type: string, format: uuid }
                passId: { type: string }
                durationHours: { type: integer, minimum: 1 }
                idempotencyKey: { type: string, description: "C2의 idempotencyKey. 같은 (sessionId, idempotencyKey)는 같은 예약을 돌려준다" }
      responses:
        "200":
          description: 같은 idempotencyKey로 이미 만들어진 예약을 그대로 돌려준다 (본문은 201과 같다)
        "201":
          description: 새 결제 대기 예약이 만들어졌다. amount는 C4가 돌려준 price다.
          content:
            application/json:
              schema:
                type: object
                required: [reservationId, status, amount, paymentRequestedAt, durationHours, lotName]
                properties:
                  reservationId: { type: string }
                  status: { type: string, enum: [PENDING_PAYMENT] }
                  amount: { type: integer }
                  durationHours: { type: integer }
                  lotName: { type: string }
                  paymentRequestedAt: { type: string, format: date-time }
        "404": { description: "PASS_NOT_FOUND" }
        "409": { description: "PENDING_EXISTS: 같은 세션에 다른 키의 결제 대기 예약이 있음. 본문에 기존 reservationId를 담는다" }
        "422": { description: "DURATION_NOT_AVAILABLE" }
        "502": { description: "UPSTREAM_FAILED: U1(C4) 호출 실패" }
  /internal/reservations/{reservationId}/payment-result:
    post:
      summary: 결제 결과를 반영하는 원자 연산. 예약이 결제 대기일 때만 적용된다. 같은 eventId를 다시 보내도 결과는 같다.
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [eventId, outcome]
              properties:
                eventId: { type: string }
                outcome: { type: string, enum: [APPROVED, FAILED] }
      responses:
        "200":
          content:
            application/json:
              schema:
                type: object
                required: [result, reservationStatus]
                properties:
                  result: { type: string, enum: [APPLIED, REJECTED] }
                  reservationStatus: { type: string, enum: [PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, CANCELLED] }
        "404": { description: "NOT_FOUND" }
```

- `APPLIED`는 예약이 결제 대기였고 결과가 반영되었다는 뜻이다. `REJECTED`는 이미 확정되었거나 취소되어 상태를 바꾸지 않았다는 뜻이며 현재 상태를 함께 돌려준다.
- 결제 결과 반영은 멱등하다. 같은 `eventId`에는 처음 낸 결과를 그대로 돌려준다.
- 결제 결과 반영은 제한 시간 2초, 200ms 간격 최대 2회 재시도를 적용한다. 예약 생성은 재시도하지 않는다.

## C6 모의 PG 웹훅 (모의 PG → U3)

```yaml
openapi: 3.0.3
info: { title: payment-service webhook, version: "1" }
paths:
  /api/payments/webhook:
    post:
      summary: 모의 PG가 결제 승인·실패를 비동기로 알리는 웹훅. 출처와 무관하게 서명을 검증한다.
      parameters:
        - name: X-Signature
          in: header
          required: true
          description: "sha256=<소문자 hex>. 본문 원문 바이트에 대한 HMAC-SHA256, 키는 환경 변수 PAYMENT_WEBHOOK_SECRET"
          schema: { type: string }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [eventId, paymentId, outcome, occurredAt]
              properties:
                eventId: { type: string }
                paymentId: { type: string }
                outcome: { type: string, enum: [APPROVED, FAILED] }
                occurredAt: { type: string, format: date-time }
      responses:
        "200":
          description: 처리 결과
          content:
            application/json:
              schema:
                type: object
                required: [handled]
                properties:
                  handled: { type: string, enum: [APPLIED, DUPLICATE, IGNORED] }
        "401": { description: "INVALID_SIGNATURE: 거부하고 상태를 바꾸지 않으며 기록한다" }
        "404": { description: "UNKNOWN_PAYMENT" }
        "502": { description: "UPSTREAM_FAILED: U2 결제 결과 반영 호출이 재시도 후에도 실패함. eventId는 처리됨으로 기록하지 않는다" }
```

- `eventId`는 U2 반영이 성공한 뒤에만 처리됨으로 기록한다. 반영에 실패하면(502) 기록하지 않으므로 같은 이벤트가 다시 오면 처음부터 처리한다.
- 모의 PG는 502를 받아도 재전송하지 않는다. [assumption] 이 경우 승인은 유실되어 예약은 결제 요청 시점부터 180초 뒤 자동 취소되며, 이 결과를 정상 동작으로 본다. 모의 PG의 재전송 정책은 Open questions에 남긴다.

- `APPLIED`는 U2가 `APPLIED`로 반영했다는 뜻, `IGNORED`는 U2가 `REJECTED`를 돌려줘서 무시하고 기록했다는 뜻, `DUPLICATE`는 이미 처리한 `eventId`라는 뜻이다.
- 모의 PG의 자기 호출도 같은 서명 검증을 거친다. 공유 비밀 값은 코드에 두지 않고 환경 변수로 주입한다.
- 웹훅 처리 실패 시 모의 PG는 재전송하지 않는다. [assumption] 재전송 정책은 모의 PG의 시나리오 범위 밖으로 둔다.

## 오류, 제한 시간, 재시도 정리

| 호출 | 제한 시간 | 재시도 | 실패 시 |
|---|---|---|---|
| C1 추천 요청 | 2초 | 화면이 사용자 동작으로 다시 전송 | 서버 오류 안내 |
| C2 결제 요청 | 2초 | 같은 `idempotencyKey`로 다시 보내도 안전 | 접수 실패 안내 |
| C4 주차권 조회 | 2초 | 200ms 간격 최대 2회 | U2가 `PASS_NOT_FOUND` 또는 502 |
| C5 예약 생성 | 2초 | 자동 재시도 없음 (멱등 키가 있어 사용자가 같은 키로 다시 보내면 안전) | U3가 502 `UPSTREAM_FAILED`, 이미 만들어진 예약이 있어도 같은 키 재요청으로 이어 감 |
| C5 결제 결과 반영 | 2초 | 200ms 간격 최대 2회 | 웹훅이 502 `UPSTREAM_FAILED`를 돌려주고 기록하며 eventId는 처리됨으로 기록하지 않음 |

## 계약 소유와 변경 규칙

- 각 계약의 명세는 제공하는 유닛이 소유한다 (표의 Owner). 변경 제안은 소비자 유닛과 합의한다.
- 호환되지 않는 변경(필드 삭제, 의미 변경)은 명세와 소비자를 같은 변경에 함께 고친다. 프로토타입이므로 경로에 버전을 두지 않는다.
- 필드 추가는 안전하게 한다. 소비자는 모르는 필드를 무시한다.
- 계약 명세는 구현 전에 확정하고, 서비스 간 시험은 이 명세를 기준으로 한 가짜 응답으로 먼저 시험한 뒤 실제 서비스로 통합 시험한다.

## Open questions

| Contract | Question | Blocks |
|---|---|---|
| C3 | Vite 프록시가 SSE를 버퍼링하지 않고 전달하는지 확인하는 시험 기준 | U4, U5 |
| C3 | NFR1 1초 예산의 구간 값(웹훅 150ms, 반영·푸시 150ms 등) 확정 | U2, U3, U4 |
| C2 | 결제 실패 뒤 실패한 후보를 다시 고를 수 있는지 | U3, U4 |
| C6 | 모의 PG의 웹훅 재전송 정책 | U3 |
| C1, C2, C3 | 모의 시나리오 필드(mockScenario)를 화면에서 어떻게 노출할지(개발용 선택 UI) | U4 |

## Assumptions & Open Questions

- [assumption] 포트 번호와 프록시 접두사는 이 계약에서 정한 값이며 변경은 계약 변경으로 다룬다.
- [assumption] 모의 시나리오 입력(`mockScenario`, `mockDelaySeconds`)은 개발·시연용 필드이고 실제 PG 연동 시 무시된다.
- 위 Open questions 표의 항목은 Functional Design과 NFR 단계에서 정한다.

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-05T09:44:09Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C2 요청 본문, C4 응답, C5 `/internal/reservations` | `Reservation.durationHours`는 C3 응답과 C5 201 응답에서 필수인데, 값의 출처가 어느 계약에도 없다. C2 요청은 `passId`와 `idempotencyKey`만, C5 요청은 `sessionId`와 `passId`만 싣고, C4 응답은 `passId`, `lotName`, `price`만 준다. 이용 시간은 C1 `query.durationHours`에만 있어 U2는 이 값을 알 수 없다. components.md의 Reservation 속성에도 `durationHours`, `lotName`, `version`, `message`가 없다. | 이용 시간을 C2와 C5 요청에 넣거나 C4 응답에 주차권의 이용 시간을 넣어 출처를 한 곳으로 정한다. 그리고 `amount` 산정 규칙(주차권 가격과 이용 시간의 관계)을 적는다. 아니면 `durationHours`를 `Reservation` 응답에서 뺀다. | Resolved (reviewer) |
| R-02 | Major | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C2 멱등 키와 C5 예약 생성, 오류·재시도 정리 표 | U3가 C5 예약 생성을 재시도 없이 2초에 끊으면, U2에는 예약이 만들어졌는데 U3는 `reservationId`를 모르는 상태가 생긴다. U3는 502를 돌려준다. 화면이 같은 `idempotencyKey`로 재요청하면 U3에는 기록이 없어 C5를 다시 부르고, U2는 `PENDING_EXISTS`(409)를 돌려준다. 그러면 사용자는 180초 자동 취소까지 결제할 수 없다. C5 생성 요청에는 멱등 키가 없고, 이미 있는 대기 예약을 조회하는 방법도 없다. 계약은 "멱등"이라고 하지만 가장 흔한 실패 경로에서 성립하지 않는다. | C5 생성 요청에 멱등 키(또는 `paymentId`)를 넣어 같은 키의 재호출이 기존 `reservationId`를 돌려주게 한다. 또는 `PENDING_EXISTS` 응답에 기존 `reservationId`를 실어 U3가 같은 키의 재요청을 복구하게 한다. 이 규칙을 C2와 C5에 명시한다. | Resolved (reviewer) |
| R-03 | Major | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C3 `/api/reservations/{reservationId}/events` | SSE 구독에 `X-Session-Id` 헤더를 필수로 정했다. 브라우저 기본 `EventSource`는 사용자 지정 헤더를 보낼 수 없어, 이 계약은 화면에서 그대로 구현할 수 없다. 화면은 fetch 스트림 방식의 구독으로 가야 하는데 그 사실이 계약에 없다. AC4.1.1과 AC4.1.4의 푸시 경로가 걸려 있다. 헤더 선택 자체의 문제가 아니라 이 엔드포인트의 구현 가능성 문제다. | 이 엔드포인트에 한해 헤더 대신 쿼리 파라미터를 허용하거나, 화면이 fetch 스트림 방식으로 구독해야 한다고 계약에 적는다. 이어서 재연결 시 헤더를 보내는 방법도 정한다. | Resolved (reviewer) |
| R-04 | Major | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C6 responses, 오류·재시도 정리 표 | "결제 결과 반영 실패 시 웹훅은 오류를 돌려주고 기록"이라고 하지만 C6는 200, 401, 404만 정의한다. U2 호출이 재시도 후에도 실패할 때의 응답 코드가 없다. 모의 PG는 재전송하지 않는다고 가정했으므로 승인이 유실되고 예약은 180초 뒤 취소된다. 이때 `eventId`를 "처리됨"으로 기록하는지 여부도 없다. 먼저 기록하면 이후 재전송이 `DUPLICATE`로 막혀 복구할 수 없다. | C6에 5xx 응답(예: `UPSTREAM_FAILED`)을 추가하고 `eventId`는 U2 반영이 성공한 뒤에만 처리됨으로 기록한다고 정한다. 재전송 정책이 가정이므로 유실 시 결과(180초 자동 취소)를 명시해 둔다. | Resolved (reviewer) |
| R-05 | Minor | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C5 payment-result 설명 | "같은 `eventId`에는 처음 낸 결과를 그대로 돌려준다"고 했는데, U2가 `eventId`를 저장한다는 근거가 components.md에 없다(`WebhookEvent`는 Payment 소유). 상태만으로 판단하면 첫 호출이 `APPLIED` 뒤 응답 유실로 재시도될 때 `REJECTED`가 돌아오고, U3는 정상 승인을 `IGNORED`로 기록한다. | U2가 `eventId`를 저장해 같은 `eventId`에는 처음 결과를 돌려준다고 Reservation 쪽 속성으로 명시한다. 아니면 이 문장을 빼고, 재시도 응답의 `REJECTED`가 현재 상태가 `CONFIRMED`이면 `IGNORED`가 아니라 성공으로 본다는 규칙을 C6에 적는다. | Unresolved |
| R-06 | Minor | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C3, C4, C5 응답, C2 오류 응답 | 여러 응답 객체에 필수 항목인 `description`이 없다(C3 `200`, C4 `200`, C5 `201`, `200`). C2의 404, 409, 502에는 본문 스키마가 없고 C5의 U1 호출 실패 응답(오류 표의 502)도 정의돼 있지 않다. C2에는 `passId` 형식 오류용 400이나 422도 없다. | 모든 응답에 `description`을 붙이고 오류 응답은 `Error` 스키마를 참조한다. C5 생성에 U1 실패 코드(502)를 추가한다. | Unresolved |
| R-07 | Minor | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C6 서명 검증 | 재생 방어는 `eventId` 중복 차단에만 의존하고, `occurredAt` 신선도 검사가 없다. 서명 비교를 상수 시간으로 하라는 말도 없고, `PAYMENT_WEBHOOK_SECRET`이 없을 때(기동 거부) 동작도 없다. 비밀 값을 환경 변수로 쓰는 점은 규칙에 맞는다. | 비밀 값이 없으면 U3가 기동을 거부한다고 적고, 서명은 상수 시간으로 비교한다고 적는다. `occurredAt` 허용 오차를 둘지 정한다. 둘지 말지는 로컬 프로토타입이므로 선택이다. | Unresolved |
| R-08 | Minor | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C2 응답 202 스키마와 C2 규칙 "같은 idempotencyKey의 재요청은 같은 paymentId와 reservationId를 돌려준다" | 같은 키 재요청은 이미 CONFIRMED·PAYMENT_FAILED·CANCELLED(180초 자동 취소 포함)가 된 예약에도 올 수 있는데, 202 응답의 status는 PENDING_PAYMENT만 허용하고 이 경우의 응답이 정의되어 있지 않다. 재시도 복구의 마지막 경우가 모호하다. | 같은 키 재요청이 종료 상태의 예약을 만났을 때 돌려줄 응답(예: 200과 현재 status, 또는 항상 현재 Reservation 상태)을 정하고 status enum을 맞춘다. 멱등 키의 유효 기간과 화면이 키를 만드는 시점(클릭당 1개, 재시도 때 재사용)도 한 줄로 적는다. | New |
| R-09 | Minor | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > 오류, 제한 시간, 재시도 정리 표 | 바깥 호출과 안쪽 호출의 제한 시간이 모두 2초로 같다. U2가 C4에서 2초 x 3회(+200ms 간격 x 2)까지 쓸 수 있어 U3의 C5 2초 제한이 먼저 끊기고, 다시 U4의 C2 2초 제한과 겹쳐 안쪽이 성공 직전이어도 바깥이 실패한다. 이런 시간 초과 뒤 복구가 바로 R-02에서 고친 경로라서 자주 밟히게 된다. | C4 재시도가 끝나는 최악 시간을 계산해 C5·C2의 제한 시간을 그보다 크게 잡거나, C4 재시도 상한을 줄여 바깥 호출보다 안쪽 총 소요가 작도록 표를 고친다. | New |
| R-10 | Minor | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C6 아래 불릿 | 모의 PG가 웹훅 처리 실패 시 재전송하지 않는다는 가정이 두 번 반복된다(502 설명 바로 아래와 마지막 불릿). 내용은 같으나 중복이라 이후 한쪽만 고쳐질 위험이 있다. | 한 곳으로 합친다. | New |

> R-01 Reviewer note: durationHours의 출처가 C1 응답 query.durationHours, C2 요청, C4 쿼리/응답, C5 요청으로 한 줄로 이어지고, 금액은 C4가 돌려준 price(요청 이용 시간 전체 합계)이며 U2가 예약 생성 때 저장한다고 명시됨. 단위·의미가 C1 price 설명과도 일치.

> R-02 Reviewer note: idempotencyKey가 C2 요청에서 C5 요청으로 이어지고 U2가 (sessionId, idempotencyKey)로 같은 예약을 돌려줌(200). PENDING_EXISTS(409) 본문에 reservationId를 담는다고 C2·C5 양쪽에 명시. U3에 결제 기록이 아직 없는 경우도 U3가 같은 키로 C5를 다시 부르면 U2가 기존 예약을 돌려주므로 일관됨(아래 새 항목의 상태 enum 문제는 별개).

> R-03 Reviewer note: SSE 엔드포인트만 sessionId 쿼리 파라미터를 허용하고 헤더가 있으면 우선, 둘 다 없으면 401 MISSING_SESSION. 재연결 시 같은 URL 사용, URL 로깅 위험은 [assumption]으로 기록. EventSource로 구현 가능.

> R-04 Reviewer note: C6에 502 UPSTREAM_FAILED가 추가되고 eventId는 U2 반영 성공 뒤에만 기록. 표·본문·OpenAPI 블록이 서로 일치하며, 모의 PG 비재전송 시 180초 뒤 취소를 정상 동작으로 본다는 가정도 Open questions에 남음.

> R-05 Reviewer note: 사람의 선택에 따라 이번에 다루지 않음. 수정된 본문에서도 같은 eventId 재호출 시 APPLIED/REJECTED 중 무엇을 돌려주는지 여전히 "처음 낸 결과"라고만 되어 있어 U2의 eventId 보관 방식은 미정.

> R-06 Reviewer note: 사람의 선택에 따라 이번에 다루지 않음. 새로 추가·변경된 응답(C3 401, C5 409/502, C6 502)에는 description이 있으나 C3 200 2건과 C5 payment-result 200, C5 200 등에는 description 또는 content가 여전히 없고, 409/502 본문 스키마도 정의되지 않음.

> R-07 Reviewer note: 사람의 선택에 따라 이번에 다루지 않음. 웹훅 재전송 공격 방지용 occurredAt 신선도 창, 상수 시간 비교, 비밀 값 누락 시 동작은 여전히 정해지지 않음.

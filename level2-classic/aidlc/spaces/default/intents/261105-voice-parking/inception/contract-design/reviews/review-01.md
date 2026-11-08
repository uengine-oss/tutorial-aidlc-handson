## Review

**Verdict:** NOT-READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-05T09:40:24Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C2 요청 본문, C4 응답, C5 `/internal/reservations` | `Reservation.durationHours`는 C3 응답과 C5 201 응답에서 필수인데, 값의 출처가 어느 계약에도 없다. C2 요청은 `passId`와 `idempotencyKey`만, C5 요청은 `sessionId`와 `passId`만 싣고, C4 응답은 `passId`, `lotName`, `price`만 준다. 이용 시간은 C1 `query.durationHours`에만 있어 U2는 이 값을 알 수 없다. components.md의 Reservation 속성에도 `durationHours`, `lotName`, `version`, `message`가 없다. | 이용 시간을 C2와 C5 요청에 넣거나 C4 응답에 주차권의 이용 시간을 넣어 출처를 한 곳으로 정한다. 그리고 `amount` 산정 규칙(주차권 가격과 이용 시간의 관계)을 적는다. 아니면 `durationHours`를 `Reservation` 응답에서 뺀다. | New |
| R-02 | Major | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C2 멱등 키와 C5 예약 생성, 오류·재시도 정리 표 | U3가 C5 예약 생성을 재시도 없이 2초에 끊으면, U2에는 예약이 만들어졌는데 U3는 `reservationId`를 모르는 상태가 생긴다. U3는 502를 돌려준다. 화면이 같은 `idempotencyKey`로 재요청하면 U3에는 기록이 없어 C5를 다시 부르고, U2는 `PENDING_EXISTS`(409)를 돌려준다. 그러면 사용자는 180초 자동 취소까지 결제할 수 없다. C5 생성 요청에는 멱등 키가 없고, 이미 있는 대기 예약을 조회하는 방법도 없다. 계약은 "멱등"이라고 하지만 가장 흔한 실패 경로에서 성립하지 않는다. | C5 생성 요청에 멱등 키(또는 `paymentId`)를 넣어 같은 키의 재호출이 기존 `reservationId`를 돌려주게 한다. 또는 `PENDING_EXISTS` 응답에 기존 `reservationId`를 실어 U3가 같은 키의 재요청을 복구하게 한다. 이 규칙을 C2와 C5에 명시한다. | New |
| R-03 | Major | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C3 `/api/reservations/{reservationId}/events` | SSE 구독에 `X-Session-Id` 헤더를 필수로 정했다. 브라우저 기본 `EventSource`는 사용자 지정 헤더를 보낼 수 없어, 이 계약은 화면에서 그대로 구현할 수 없다. 화면은 fetch 스트림 방식의 구독으로 가야 하는데 그 사실이 계약에 없다. AC4.1.1과 AC4.1.4의 푸시 경로가 걸려 있다. 헤더 선택 자체의 문제가 아니라 이 엔드포인트의 구현 가능성 문제다. | 이 엔드포인트에 한해 헤더 대신 쿼리 파라미터를 허용하거나, 화면이 fetch 스트림 방식으로 구독해야 한다고 계약에 적는다. 이어서 재연결 시 헤더를 보내는 방법도 정한다. | New |
| R-04 | Major | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C6 responses, 오류·재시도 정리 표 | "결제 결과 반영 실패 시 웹훅은 오류를 돌려주고 기록"이라고 하지만 C6는 200, 401, 404만 정의한다. U2 호출이 재시도 후에도 실패할 때의 응답 코드가 없다. 모의 PG는 재전송하지 않는다고 가정했으므로 승인이 유실되고 예약은 180초 뒤 취소된다. 이때 `eventId`를 "처리됨"으로 기록하는지 여부도 없다. 먼저 기록하면 이후 재전송이 `DUPLICATE`로 막혀 복구할 수 없다. | C6에 5xx 응답(예: `UPSTREAM_FAILED`)을 추가하고 `eventId`는 U2 반영이 성공한 뒤에만 처리됨으로 기록한다고 정한다. 재전송 정책이 가정이므로 유실 시 결과(180초 자동 취소)를 명시해 둔다. | New |
| R-05 | Minor | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C5 payment-result 설명 | "같은 `eventId`에는 처음 낸 결과를 그대로 돌려준다"고 했는데, U2가 `eventId`를 저장한다는 근거가 components.md에 없다(`WebhookEvent`는 Payment 소유). 상태만으로 판단하면 첫 호출이 `APPLIED` 뒤 응답 유실로 재시도될 때 `REJECTED`가 돌아오고, U3는 정상 승인을 `IGNORED`로 기록한다. | U2가 `eventId`를 저장해 같은 `eventId`에는 처음 결과를 돌려준다고 Reservation 쪽 속성으로 명시한다. 아니면 이 문장을 빼고, 재시도 응답의 `REJECTED`가 현재 상태가 `CONFIRMED`이면 `IGNORED`가 아니라 성공으로 본다는 규칙을 C6에 적는다. | New |
| R-06 | Minor | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C3, C4, C5 응답, C2 오류 응답 | 여러 응답 객체에 필수 항목인 `description`이 없다(C3 `200`, C4 `200`, C5 `201`, `200`). C2의 404, 409, 502에는 본문 스키마가 없고 C5의 U1 호출 실패 응답(오류 표의 502)도 정의돼 있지 않다. C2에는 `passId` 형식 오류용 400이나 422도 없다. | 모든 응답에 `description`을 붙이고 오류 응답은 `Error` 스키마를 참조한다. C5 생성에 U1 실패 코드(502)를 추가한다. | New |
| R-07 | Minor | aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/contract-summary.md > C6 서명 검증 | 재생 방어는 `eventId` 중복 차단에만 의존하고, `occurredAt` 신선도 검사가 없다. 서명 비교를 상수 시간으로 하라는 말도 없고, `PAYMENT_WEBHOOK_SECRET`이 없을 때(기동 거부) 동작도 없다. 비밀 값을 환경 변수로 쓰는 점은 규칙에 맞는다. | 비밀 값이 없으면 U3가 기동을 거부한다고 적고, 서명은 상수 시간으로 비교한다고 적는다. `occurredAt` 허용 오차를 둘지 정한다. 둘지 말지는 로컬 프로토타입이므로 선택이다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 직접 대조(연동 지점 표 6행 대 C1~C6) | PASS | unit-of-work-dependency.md의 연동 지점 표 6행과 간선(U2→U1, U3→U2, U4→U1, U2, U3)이 C1~C6에 모두 대응한다. U5는 계약이 필요한 간선이 없다. 순환은 없다. |
| 직접 대조(YAML 내부 일관성) | PARTIAL | C1의 `$ref`(`Recommendation`, `Error`)와 C3의 `Reservation`은 같은 블록에 선언돼 있다. C2, C4, C5, C6의 블록은 YAML 구조상 유효하다. 응답 `description` 누락과 일부 오류 스키마 누락은 위 Minor에 적었다. |
| 직접 대조(스토리 오류 경로) | PASS | AC1.1.2와 AC1.1.3은 422(`MISSING_PLACE`, `MISSING_DURATION`)로, AC3.1.3은 409 `PENDING_EXISTS`로 도달 가능하다. AC3.2.x는 `FAILED`와 `PAYMENT_FAILED`로, AC4.1.2는 `DUPLICATE`로, AC4.1.3은 401로, AC4.1.5와 AC4.2.3은 `REJECTED`와 `IGNORED`와 `CANCELLED`로, AC4.1.4는 스냅샷 재전송과 `/current`로 표현된다. 단, 위 첫째와 둘째 Major가 있는 경로(AC3.1.3 복구, 이용 시간 표시)는 정상 동작하지 않는다. |

### Summary

계약 표와 의존 간선은 서로 빠짐없이 맞고 오류 경로도 대체로 표현되지만, 이용 시간의 출처 누락, 예약 생성 타임아웃 뒤의 멱등 복구 불가, SSE 헤더의 브라우저 구현 불가, 웹훅 반영 실패 응답 미정의의 4개 Major가 있어 구현자가 추측 없이 만들 수 없다. 이 4건을 계약에 반영하면 READY에 가깝다.

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-05T07:11:38Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | frontend/src/App.vue (select, onReservationStatus, pollPayment, startCountdown); aidlc/spaces/default/intents/261105-voice-parking/construction/code-generation/code-summary.md > 실행하지 않은 테스트와 검증 | 이 프로토타입의 성공 기준(결제 후 예약 완료가 화면에 보임, FR4.2/NFR1)을 실제로 이어 주는 코드는 App.vue의 SSE 구독, 결제 폴링, 카운트다운, 취소 화면 전환입니다. 이 부분에는 자동 테스트가 없고 브라우저에서 열어 본 적도 없다고 요약서가 스스로 밝힙니다. 프런트 vitest 12개는 하위 컴포넌트와 events.js만 확인합니다. 서비스 쪽은 테스트가 모두 통과하지만(아래 도구 결과), 화면 전체 흐름은 증거가 없습니다. | 승인 전에 `run-all.sh`로 실제 브라우저에서 입력, 추천, 결제, 승인, 예약 완료와 실패 후 재결제, 180초 취소 흐름을 한 번 직접 확인하고 결과를 요약서에 적으세요. 또는 App.vue를 EventSource/fetch 가짜로 마운트해 승인 → done 전환 한 건을 테스트로 추가하세요. | New |
| R-02 | Minor | backend/payment-service/src/main/java/com/voiceparking/payment/service/PaymentService.java > handleWebhook (javadoc); backend/payment-service/src/main/java/com/voiceparking/payment/mockpg/MockPgSimulator.java > scheduleWebhook | 예약 서비스에 닿지 못하면 결제를 PENDING으로 두고 예외를 올려 "PG가 재시도한다"고 설명하지만, 모의 PG는 실패를 로그에 남기고 끝낼 뿐 재시도하지 않습니다. 따라서 일시적 장애 한 번이면 결제는 PENDING에 머물고 예약은 180초 뒤 취소됩니다. 설명과 동작이 어긋납니다. | 모의 PG에 짧은 재시도(예: 2~3회)를 넣거나, 재시도가 없다는 점을 javadoc과 RUN.md의 한계로 정직하게 고치세요. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/code-generation/code-summary.md > 테스트 현황, 실행하지 않은 테스트와 검증; backend/reservation-service/src/test/java/com/voiceparking/reservation/api/ReservationApiTest.java (SSE_구독_중_확정되면_1초_안에...) | NFR1의 1초 검증은 예약 서비스 안(확정 호출 → SSE 이벤트)에서만 측정합니다. 요구사항의 시작점은 "승인 웹훅을 서버가 받은 시점"이라 결제 서비스 → 예약 서비스 HTTP 구간과 화면 반영이 빠져 있습니다. 요약서가 이 한계를 밝히고 있어 정직하지만, NFR1을 통과로 읽을 수 있는 표현("각각 검증했습니다")이 있습니다. | NFR1 항목에 "서비스 간 HTTP 구간은 미측정"을 같은 문단에 명시하거나, 두 서비스를 띄운 상태로 웹훅 수신부터 SSE 수신까지 한 번 측정해 기록하세요. | New |
| R-04 | Minor | backend/payment-service/src/test/java/com/voiceparking/payment/service/PaymentServiceTest.java; backend/payment-service/src/main/java/com/voiceparking/payment/client/HttpReservationClient.java > confirm | payment → reservation 계약(2xx=확정, 409=취소됨, 404=없음)은 양쪽 모두 가짜(ReservationClient 스텁, MockMvc 단독)로만 검증됩니다. HttpReservationClient 자체의 상태코드 매핑을 확인하는 자동 테스트가 없고, 실제 연결은 손으로 한 번 해 본 것뿐입니다. | HttpReservationClient의 200/409/404/5xx 매핑을 MockRestServiceServer 등으로 한 건씩 테스트하거나, 현재 한계로 요약서에 적으세요. | New |
| R-05 | Minor | backend/payment-service/src/main/java/com/voiceparking/payment/api/PaymentController.java > create; backend/reservation-service/src/main/java/com/voiceparking/reservation/api/ReservationController.java > confirm; RUN.md | 결제 생성 시 금액은 클라이언트가 보낸 값이고 예약 존재 여부·금액 일치를 확인하지 않습니다. 또한 `/internal/reservations/{id}/confirm`과 `/api/mock-pg/*`는 인증 없이 열려 있어, 누구나 승인 없이 예약을 확정할 수 있습니다. RUN.md는 웹훅 서명 부재만 적고 이 두 가지는 적지 않았습니다. 프로토타입 범위에서는 허용되지만 보안 규칙(우회 코드는 표시)에 비춰 기록이 필요합니다. | RUN.md와 code-summary.md의 알려진 한계에 "내부 확정 API와 모의 PG 엔드포인트는 인증이 없는 시연용"이라고 추가하세요. 금액 검증은 선택 사항으로 남겨도 됩니다. | New |
| R-06 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/code-generation/traceability.json > coverage (FR1.1, FR2.1, FR2.3, FR3.1, FR3.2, FR3.4, FR4.1) | 같은 파일 안에서 어떤 요구사항은 구현 파일(QueryParser, ScoreCalculator 등)을, 어떤 요구사항은 테스트 파일(QueryParserTest, RecommendationApiTest 등)을 target으로 삼아 일관성이 없습니다. 모든 target 파일이 실제로 존재함은 확인했습니다. 그러나 FR3.1은 PaymentController만 가리키고 전용 테스트가 없어 "결제 시작 후 결제 대기 상태" 확인이 어디 있는지 추적되지 않습니다. | 각 요구사항에 구현 target과 검증 테스트 target을 함께 적으세요(예: FR3.1 → PaymentController + PaymentApiTest의 생성 테스트). | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `mvn -q -f backend/pom.xml -pl reservation-service -am test` | 종료 코드 0 (조용한 모드, 실패 없음; 19개로 요약서가 보고) | 180초 경계(179초 대기, 180초 정확히 취소, 200초, 늦은 승인), 확정 후 SSE 1초 이내 테스트가 모두 통과했습니다. |
| `mvn -f backend/pom.xml -pl recommendation-service,payment-service -am test` | recommendation Tests run: 18, Failures: 0, Errors: 0; payment Tests run: 17, Failures: 0, Errors: 0; BUILD SUCCESS | 40/40/20 점수·동점 규칙, 파서, 멱등 웹훅, 늦은 승인, 404, 502 경우 통과. 요약서의 테스트 수(18/17)와 일치합니다. |
| `npm --prefix frontend run test -- --run` | Test Files 3 passed, Tests 12 passed | 요약서의 12개와 일치. App.vue는 대상에 포함되지 않습니다. |
| 코드 직접 확인 | 정확히 180초는 `!now.isBefore(deadlineAt)`로 만료 처리, 취소된 예약 확정은 `InvalidTransitionException`(409), 중복 웹훅은 `isPending()` 가드, `@EnableScheduling` 있음, 점수 비중·동점(가격 → ID)은 ScoreCalculator와 일치 | 계획의 설계 결정과 구현이 일치합니다. 요청 검증과 클라이언트 타임아웃 및 오류 변환도 확인했습니다. |

### Summary

계획한 14개 단계와 FR1~FR4, NFR1~NFR2는 구현되어 있고, 직접 돌린 서비스 3개와 프런트 테스트가 모두 통과하며, 경계 조건(정확히 180초 취소, 늦은 승인, 멱등 웹훅, 점수와 동점 규칙)이 코드와 테스트에서 확인됩니다. 가장 큰 공백은 App.vue의 화면 연결이 자동 테스트도 실제 브라우저 확인도 없다는 점이며, 승인 전에 한 번 손으로 확인하는 것을 권합니다. 나머지는 문서·추적성 수준의 사소한 항목입니다.

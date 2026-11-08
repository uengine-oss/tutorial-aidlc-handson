## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-05T07:32:20Z
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
| R-07 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/code-generation/code-summary.md > 주요 구현 결정 (서비스 간 호출 실패 항목) 및 테스트 현황 (ERROR 한 줄 항목) | 같은 문서의 "변경 요청 반영" 절은 모든 결제 오류가 402라고 하는데, 위쪽 두 곳은 여전히 "예약 서비스 호출 실패 시 결제 서비스는 502를 돌려준다", "502 경우를 일부러 만든 것"이라고 적혀 있습니다. 실제 코드(ApiExceptionHandler.upstream)와 테스트(예약_서비스_장애면_402이고_결제는_PENDING)는 402 RESERVATION_UNAVAILABLE입니다. 읽는 사람이 어느 쪽이 맞는지 알 수 없습니다. | 두 곳의 "502"를 "402 + RESERVATION_UNAVAILABLE"로 고칩니다. | New |
| R-08 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/code-generation/unit-test-instructions.md > 테스트 구성 표, payment 웹훅 처리 행 ("모르는 결제 ID는 404"); code-generation-plan.md > Step 10 ("모르는 결제 404") | 테스트 안내와 계획 Step 10은 모르는 결제 ID를 404로 설명하지만, 구현과 테스트(모르는_결제_ID_웹훅은_402와_표준_오류_객체, 모의_PG는_모르는_결제에_402)는 402 PAYMENT_NOT_FOUND입니다. 계획의 17번째 줄은 변경 후 규칙을 따르도록 고쳐 놓아 같은 문서 안에서 서로 어긋납니다. | 표의 행과 Step 10 문구를 402 PAYMENT_NOT_FOUND로 고칩니다. | New |
| R-09 | Minor | backend/payment-service/src/main/java/com/voiceparking/payment/api/ApiExceptionHandler.java > upstream(ReservationClientException) 및 service/PaymentService.java > handleWebhook의 Javadoc | "결제는 PENDING으로 남고 PG가 웹훅을 재시도해야 한다"고 적혀 있지만, 이제 이 경우 웹훅 응답이 4xx(402)입니다. 일반적인 PG는 4xx를 재시도하지 않으므로 일시적인 예약 서비스 장애가 영구 실패처럼 됩니다. 모의 PG(MockPgSimulator, HttpWebhookSender)도 재시도 없이 로그만 남기고 끝냅니다. 그 결과 사용자가 시연 버튼을 다시 누르거나 180초 뒤 예약이 취소될 때까지 PENDING으로 남습니다. 이는 402 통일 규칙을 따른 대가인데 code-summary.md에 공개돼 있지 않습니다. | code-summary.md의 "규칙에서 뺀 것/범위가 넓은 점" 옆에 이 대가를 한 줄 적고, 코드 주석의 "PG가 재시도" 문구를 현재 동작에 맞게 고칩니다. | New |
| R-10 | Minor | backend/payment-service/src/main/java/com/voiceparking/payment/api/ApiExceptionHandler.java > unexpected(Exception) | 공개 사항대로 Exception 전체를 받으므로 없는 경로(404)·지원하지 않는 메서드(405)·지원하지 않는 Content-Type(415) 같은 프레임워크 오류도 402 PAYMENT_INTERNAL_ERROR가 되고, 이 경로를 검증하는 테스트도 없습니다. 이 방식이 타당한 선택인지, 숨기는 내부 정보가 없는지는 확인했습니다. 응답 본문에는 고정 문구만 있고 상세는 로그에만 남으며, 테스트(예상치_못한_예외도_402…내부정보는_숨긴다)가 이를 검증합니다. 다만 오타 URL이 "결제 필요"로 보이는 점은 클라이언트에 혼란을 줍니다. 그리고 처리기가 payment-service 전체에 걸리므로, 앞으로 결제와 무관한 엔드포인트를 이 서비스에 추가하면 그 오류도 402가 됩니다. | 프로토타입이므로 허용 가능합니다. 의도적인 선택이라는 점을 처리기 Javadoc에 한 줄 남기거나, 프레임워크 오류(NoResourceFoundException, HttpRequestMethodNotSupportedException)만 별도 상태로 되돌릴지 사람이 결정합니다. | New |
| R-11 | Minor | frontend/src/App.vue > pollPayment(), select() / guard() | 결제 상태 폴링의 catch 블록이 오류를 아무 표시 없이 삼킵니다. 그래서 GET /api/payments/{id}가 402 PAYMENT_NOT_FOUND 같은 표준 오류를 줘도 code/message를 읽지 않고 1초 간격으로 계속 폴링합니다. 또 select()에서 결제 생성이 402로 실패하면 그 시점의 step이 아직 recommend여서 표준 오류가 결제 화면이 아니라 일반 오류 줄에 나옵니다(메시지는 읽습니다). 이때 PENDING 예약이 만들어진 채 남습니다. | 폴링 catch에서 표준 오류의 code가 있으면 paymentError에 반영하고 폴링을 멈춥니다(construction 규칙의 "조용한 실패 금지"). 브라우저 확인(사용자 담당) 때 이 경로도 보도록 합니다. | New |

> R-01 Not re-checked this round

> R-02 Not re-checked this round

> R-03 Not re-checked this round

> R-04 Not re-checked this round

> R-05 Not re-checked this round

> R-06 Not re-checked this round

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `mvn -q -f backend/pom.xml -pl payment-service -am test` | 통과. surefire 보고서: PaymentApiTest 10건, PaymentServiceTest 9건, 실패 0, 오류 0, 건너뜀 0 (합계 19건, code-summary의 19건과 일치). 의도한 ERROR 스택트레이스가 로그에 출력됨 | 402 + {code, message} 계약이 PAYMENT_VALIDATION_FAILED, PAYMENT_NOT_FOUND(웹훅·모의 PG 승인/실패), PAYMENT_ALREADY_APPROVED, RESERVATION_UNAVAILABLE, PAYMENT_INTERNAL_ERROR(내부정보 비노출) 전부에 대해 MockMvc로 검증됨. 약화된 테스트는 없음 |
| `npm --prefix frontend run test -- --run` | 통과. 4개 파일 16건 (api.spec 2, RecommendationCards 3, reservation-done 5, PaymentPanel 6) | api.js가 code/message를 읽어 ApiError로 던지고, PaymentPanel이 표준 오류를 `data-code`와 함께 보여 주는 것을 검증함. 재결제 버튼 유지도 확인함 |
| traceability.json 대상 파일 존재 확인 | 13개 대상 모두 존재 | 결제 관련 대상(PaymentController, PaymentService, PaymentPanel.spec.js)은 그대로 유효함 |
| recommendation / reservation 서비스 테스트 | 이번 개정 범위가 아니라 실행하지 않음 | 결제 서비스 외 코드는 바뀌지 않은 것으로 봄. code-summary의 18/19건 수치는 직접 확인하지 않음 |

### Summary

결제 오류 통일(Step 15)은 코드·테스트·프런트 모두 실제로 동작하며, `result=FAILED` 웹훅을 200으로 두고 catch-all을 넓게 둔 선택은 타당하고 code-summary에 정직하게 공개돼 있습니다. 남은 문제는 문서 정합성(구 502/404 서술이 남음)과, 402가 4xx라서 웹훅 재시도 의미가 달라지는 점이 공개되지 않은 것으로, 구조를 막는 결함은 아닙니다. R-01은 요청대로 제기하지 않았습니다.

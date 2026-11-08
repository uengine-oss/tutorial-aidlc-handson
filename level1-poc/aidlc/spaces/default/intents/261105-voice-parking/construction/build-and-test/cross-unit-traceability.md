# 요구사항 추적 결과: VoiceParking 프로토타입

근거: `aidlc engine sensor-traceability --stage build-and-test` 출력. 이 작업은 Unit 없이 한 번에 만들었으므로(zero-Unit) 담당 단계는 모두 `code-generation`입니다.

**판정: 통과 (미충족 항목 없음, 13개 중 13개 충족)**

| 요구사항 | 담당 단계 | 구현·검증 대상 파일 | 검증 테스트 |
|---|---|---|---|
| FR1.1 | code-generation | `backend/recommendation-service/.../service/QueryParser.java` | QueryParserTest |
| FR1.2 | code-generation | `backend/recommendation-service/.../service/QueryParserTest.java` | QueryParserTest(경계 포함) |
| FR2.1 | code-generation | `backend/recommendation-service/.../service/ScoreCalculator.java` | ScoreCalculatorTest |
| FR2.2 | code-generation | `backend/recommendation-service/.../api/RecommendationApiTest.java` | RecommendationApiTest, TicketRepositoryTest |
| FR2.3 | code-generation | `backend/recommendation-service/.../service/ExplanationGenerator.java` | RecommendationApiTest, `frontend/tests/RecommendationCards.spec.js` |
| FR3.1 | code-generation | `backend/payment-service/.../api/PaymentController.java` | PaymentApiTest |
| FR3.2 | code-generation | `backend/payment-service/.../service/PaymentService.java` | PaymentServiceTest, PaymentApiTest |
| FR3.3 | code-generation | `frontend/tests/PaymentPanel.spec.js` | PaymentPanel.spec.js, PaymentServiceTest |
| FR3.4 | code-generation | `backend/reservation-service/.../service/ExpiryScheduler.java` | ReservationServiceTest, ReservationTest |
| FR4.1 | code-generation | `backend/reservation-service/.../service/ReservationService.java` | ReservationServiceTest, ReservationApiTest |
| FR4.2 | code-generation | `frontend/src/components/ReservationDone.vue` | reservation-done.spec.js |
| NFR1 | code-generation | `backend/reservation-service/.../api/ReservationApiTest.java` | ReservationApiTest(SSE 1초), 실제 기동 10회 측정 |
| NFR2 | code-generation | `backend/reservation-service/.../service/ReservationServiceTest.java` | ReservationServiceTest(179초·180초·늦은 승인) |

(경로의 `...`는 `src/main/java/com/voiceparking/<서비스>` 또는 `src/test/java/com/voiceparking/<서비스>`를 줄여 쓴 것입니다.)

## 알려진 공백

- FR3.1은 전용 테스트 이름이 없고 결제 생성 테스트(PaymentApiTest)에서 함께 확인됩니다.
- 화면(`App.vue`)이 SSE를 받아 예약 완료를 보여 주는 연결은 자동 테스트가 없습니다. FR4.2와 NFR1의 "화면 표시" 부분은 사용자의 브라우저 확인에 남아 있습니다.

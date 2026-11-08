# u3-payment-service 코드 요약

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] code-generation-plan.md(Step 1~18, 결정 E1~E8), unit-test-instructions.md, functional-design/, nfr-requirements/, nfr-design/, contract-summary.md(C2, C5, C6), u2-reservation-service의 code-summary.md

## 만든 것

결제 요청을 받아 예약 서비스(u2)에 대기 예약을 만들고 같은 프로세스의 모의 PG에 결제를 요청하며, 모의 PG가 보내는 HMAC 서명된 승인·실패 웹훅을 멱등하게 처리해 u2의 원자 연산으로 반영하는 서비스다. Java 17, Spring Boot 3.5.7(Spring MVC), Maven. 포트 8083, 기본 바인딩은 루프백(`127.0.0.1`). 저장소는 이 서비스의 인메모리다.

| 위치 | 내용 |
|---|---|
| `pom.xml` (루트) | 모듈 `services/u3-payment-service` 추가 |
| `services/u3-payment-service/pom.xml` | Spring Boot web·validation·actuator, JaCoCo 라인 80% 하한 검사 |
| `…/payment/` | `PaymentService`(결제 요청·웹훅 처리), `PaymentStore`(인메모리, 이벤트·키·Payment별 잠금), `Payment`, `PaymentStatus`, `WebhookEvent`, `PaymentGateway`와 `PgPayment`(모의 PG 접점), `ReservationClient`/`HttpReservationClient`(u2 호출, 호출 전체 총 시간 제한), `ErrorCode`, `PaymentException` 외 값 타입 |
| `…/signature/HmacSigner` | HMAC-SHA256 서명 계산(웹 검증기와 모의 PG가 함께 씀) |
| `…/pg/` | `MockPaymentGateway`(APPROVE·DELAY·FAIL, 스케줄러와 발송 실행기 분리), `WebhookCaller`(서명된 자기 호출). 걷어낼 수 있는 구성 요소 |
| `…/web/` | `PaymentController`(C2), `WebhookController`(C6), `WebhookBodyFilter`(8KB 한정 읽기), `SignatureVerifier`(상수 시간 비교), `ApiExceptionHandler`, `RequestLogFilter`, `SessionIds`, `dto/` |
| `…/config/` | `PgConfig`(비밀 값을 엄격히 읽음, 없으면 기동 실패), `ReservationClientConfig`, `TimeConfig` |
| `src/main/resources/application.properties` | 포트·바인딩·u2 주소·웹훅 주소·비밀 값(기본값 없음)·health만 노출 |
| `src/test/java/…` | 시험 229개와 지원 클래스(`support/`) |

## 실행 방법

저장소 루트에서:

```
mvn -q -pl services/u3-payment-service test      # 시험
mvn -q -pl services/u3-payment-service verify    # 시험 + JaCoCo 80% 하한
mvn -pl services/u3-payment-service spring-boot:run   # 서비스 기동 (포트 8083, PAYMENT_WEBHOOK_SECRET 필요)
```

환경 변수: `PAYMENT_WEBHOOK_SECRET`(필수, 기본값 없음), `SERVER_PORT`(8083), `SERVER_ADDRESS`(127.0.0.1), `RESERVATION_SERVICE_URL`(http://127.0.0.1:8082), `WEBHOOK_URL`(모의 PG가 부르는 자기 웹훅 주소, 기본 http://127.0.0.1:8083/api/payments/webhook).

## 시험 결과

- 전체 229개 가운데 `PerformanceTest`를 뺀 223개는 모두 통과했다. 라인 커버리지 94.3%(516/547), 하한 80% 통과(`PerformanceTest`를 뺀 실행의 수치).
- **미해결: 성능 시험 두 개가 이 머신에서 간헐적으로 실패한다.** 중복 웹훅 응답 20ms와 서명 거부 20ms의 100회 최댓값이 호스트 부하(부하 평균 110~250, 코어 16개) 때문에 25~200ms로 튀는 경우가 있다. 중앙값은 0.1~1ms다. 서비스 코드가 없는 단독 Java 루프도 같은 시점에 최대 38ms까지 튀었다. 같은 부하에서 웹훅 150ms(최대 44~79ms), 결제 요청 500ms(최대 52~118ms), 동시 10 세션(최대 62~137ms)은 통과했다. 목표 수치와 JaCoCo 하한은 낮추지 않았고 시험은 엄격하게 두었다. 사람의 결정(2026-11-07): 부하가 낮을 때 `mvn -q -pl services/u3-payment-service verify`를 다시 돌려 확인한다. 이 확인이 끝나기 전까지 `mvn verify`는 부하에 따라 실패할 수 있다.
- Red 증거: 계층마다 시험을 먼저 썼고 첫 실행은 구현 클래스가 없어 컴파일 실패였다. 런타임 실패 하나(`PaymentTest.aTerminalStatusNeverChanges`: 종료 상태에서 전이를 부를 때 예외 순서)는 구현을 고쳤다.
- u1 시험 65개, u2 시험 145개는 그대로 통과한다(두 서비스 파일은 건드리지 않았다).

## 실행하지 않은 시험

- 없음. 작성한 시험을 모두 실행했다. `LoopbackBindingTest`의 비루프백 주소 접속 거부 시험은 이 머신에 비루프백 IPv4 주소가 있을 때만 돌고 없으면 건너뛴다.

## 계획과 달라진 점

- `config/PaymentProperties`를 만들지 않고 `PgConfig`에서 `@Value`로 비밀 값을 엄격히 읽는다(해석되지 않은 `${PAYMENT_WEBHOOK_SECRET}`이 리터럴로 통과하는 것을 막으려고).
- `pg/PgOutcome` 대신 `payment.Outcome`을 공유한다. 계획에 없던 값 타입(`MockScenario`, `Handled`, `ApplyResult`, `ApplyReply`, `CreateCall`, `PaymentCommand`, `PaymentReceipt`, `WebhookCall`, `ErrorCode`)을 더했다.
- E2: u2 호출의 총 시간 제한은 JDK `HttpClient.sendAsync` + `future.get(남은 시간)`으로 구현했다. 넘으면 호출을 취소한다.
- u2 결과 반영은 연결 실패·시간 초과·5xx만 재시도하고 400·404 같은 확정 거절은 바로 502로 끝낸다(결과 코드는 사양과 같다).
- 같은 (세션, 키)로 u2가 201을 줬는데 로컬에 Payment가 이미 있으면 새로 만들지 않고 재사용한다.
- 웹훅 로그 값의 제어 문자·공백·`=`는 `_`로 바꾼다.

## 다른 유닛에 넘기는 사항

- **u4-web-ui, u5-local-run (필수)**: Vite 프록시는 `POST /api/payments`만 정확 일치로 이 서비스에 연결하고 `/api/payments/webhook`은 연결하지 않는다(접두사로 연결하면 웹훅 경로가 브라우저에 열린다. 서명 검증이 마지막 방어선이다). 브라우저 경유 웹훅 호출이 닿지 않는지 연동 시험에 넣는다. 공유 계약(contract-summary.md)은 `/api/payments`를 접두사로 적고 있으나 검토가 끝난 문서라 고치지 않았다.
- **u4-web-ui**: 같은 키 재요청에서 예약이 이미 끝났으면 응답이 200이고 `status`가 현재 상태다(202·PENDING_PAYMENT 고정이 아님). 결제 요청의 세션 오류는 400 MISSING_SESSION이다. u2에서 오는 구독 상한 503 `SUBSCRIPTION_LIMIT`은 일반 오류로 처리한다. 개발용 `mockScenario`/`mockDelaySeconds` 필드는 개발·시연용이며 노출 방식은 u4가 정한다. DELAY에서 `mockDelaySeconds`가 0이면 200초 지연이다.
- **u5-local-run**: `PAYMENT_WEBHOOK_SECRET`을 이 서비스 한 곳에만 주입하고, 루프백 바인딩 기본값을 바꾸지 않는다. 기동 순서는 u1 → u2 → u3. 병합 전 검증 스크립트에 `mvn verify`를 넣는다.

## 알려진 사항

- `services/u3-payment-service/target/`은 `.gitignore`에 없다. `.gitignore`는 사람의 파일이라 건드리지 않았다.
- 인메모리 저장소라 재시작하면 결제 기록과 예약된 모의 PG 알림이 사라진다(설계된 동작). 예약은 u2가 자동 취소한다.
- 코드 생성 중 개발자 에이전트가 작업 공간 밖에 임시 로그 파일을 한 번 만들었다가 지웠다. 남은 파일은 없다.

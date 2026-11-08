# u3-payment-service 단위 시험 안내

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] code-generation-plan.md, nfr-requirements/tech-stack-decisions.md(JUnit 5, AssertJ, Spring Boot Test, JaCoCo), nfr-design/performance-design.md(측정 방법), team.md(TDD, 80% 하한)

## 사전 준비

- Java 17, Maven 3.9 (이 머신에 설치됨). 의존성을 내려받으려면 네트워크 접근이 필요하다.
- Docker는 쓰지 않는다. 다른 서비스(u2)를 띄울 필요가 없다. u2 호출은 시험에서 가짜로 대체한다.
- 시험은 비밀 값을 시험용 상수로 주입한다. 실행 때 `PAYMENT_WEBHOOK_SECRET`을 따로 줄 필요는 없다(기동 거부 시험은 값을 비워 확인한다).

## 이 유닛의 시험 실행 명령

저장소 루트에서 실행한다. 이 유닛에만 한정된 명령이다.

```
mvn -q -pl services/u3-payment-service test
```

한 시험만 실행하려면 `-Dtest=<클래스명>`을 덧붙인다 (예: `-Dtest=PaymentServiceWebhookTest`).

## 커버리지

- 목표: 라인 커버리지 80% 이상 (팀 규칙 하한, 낮추지 않는다).
- JaCoCo 하한 검사는 시험이 모두 통과해도 하한 미달이면 종료 코드가 0이 아니게 만든다. 그래서 시험 명령과 따로 실행한다.

```
mvn -q -pl services/u3-payment-service verify
```

- 프로젝트 전체 하한은 Build and Test가 전체 시험으로 확인한다. 이 유닛 단독 실행의 하한 결과는 참고용이며, 선택된 시험이 모두 통과하고 하한만 미달이면 시험 실패가 아니라 그 사실을 기록한다.

## 시험 구성 (약 45개)

| 시험 | 대상 | 확인하는 것 |
|---|---|---|
| PaymentTest | 도메인 | 초기 상태, 허용 전이, 종료 상태 불변, WebhookEvent handled |
| PaymentStoreTest | 저장소 | 결제·키·이벤트 조회, 서로 다른 키·이벤트·Payment의 잠금이 막지 않음, 같은 키 순서대로, `tryLock` 대기 상한 |
| HmacSignerTest | 서명 | 알려진 벡터, 형식, 상수 시간 비교 |
| PaymentServiceRequestTest | 결제 요청 | 새 결제, 형식 오류, u2 오류 전달·502, 같은 키 재요청(종료된 예약 포함), PgRequest 없는 Payment, 같은 키 동시 3건, 첫 호출 무응답 시 둘째 요청 마감, 실패 뒤 새 키 |
| PaymentServiceWebhookTest | 웹훅 처리 | 새 이벤트, 같은 eventId 순차·동시, REJECTED 무시, 종료된 Payment, u2 실패·재전송, 같은 Payment 다른 eventId 동시, 알 수 없는 결제, 잠금 대기 초과 |
| HttpReservationClientTest | u2 호출 | 예약 생성 재시도 없음·마감 1.5초(응답을 흘려도), 결과 반영 시도당 2초·간격 200ms·최대 3회 |
| MockPaymentGatewayTest | 모의 PG | APPROVE·DELAY(0이면 200초)·FAIL, 서명된 알림, 한 Payment에 한 번, 실패 로그 구분, 큐 거부 |
| PaymentControllerTest | C2 웹 계층 | 202·200·400 두 종류·404·409·422·502 |
| WebhookControllerTest | C6 웹 계층 | 200 세 종류·401 네 경우·400(형식·8KB, 크기가 서명보다 먼저)·404·502 |
| ApiExceptionHandlerTest | 오류 처리 | 500 INTERNAL_ERROR와 공통 형식, 본문에 입력 원문 없음 |
| RequestLogFilterTest | 로그 | 경로·상태·시간·세션 앞 8자, 본문·서명 값 비기록, 웹훅 결과·거부·무시·모의 PG 원인 코드 로그 |
| StartupSecretTest | 기동 | 비밀 값이 없거나 비어 있으면 기동 거부 |
| PerformanceTest | 성능 | 예열 20회 제외 100회 최댓값: 150ms/20ms/20ms/500ms, 응답 없는 u2로 502까지 1.7초, 지연 알림 50건, 동시 10 세션 |
| NoSecretsTest | 비밀 값 | `src/main`의 소스·설정에 비밀 값 패턴 없음 |
| LoopbackBindingTest | 바인딩 | 기본 `server.address`가 루프백 |
| PackageBoundaryTest | 구조 | `payment`·`web`·`signature`가 `pg`를 import하지 않음, `pg`가 `web`을 import하지 않음 |
| HealthEndpointTest | 상태 확인 | `/actuator/health`가 UP |

## 모의·시험 데이터

- 시각: 주입하는 시험용 `Clock`과 스케줄러를 쓰고 시계를 앞으로 돌려 지연을 검증한다. 실제 스케줄러 경로는 짧은 지연(2초 안팎)으로 한 번 확인한다.
- u2 호출: 가짜 `ReservationClient`(201, 200, 409, 404, 422, 지연, 응답 없음, 응답을 흘림, 실패)를 쓴다. `HttpReservationClientTest`만 가짜 HTTP 서버로 재시도 타이밍을 확인한다.
- 모의 PG의 자기 호출: 시험용 웹훅 수신 서버가 서명된 본문을 받아 확인한다.
- 동시성 시험은 여러 스레드로 같은 요청을 동시에 보내고 결과를 여러 번 반복해 확인한다.

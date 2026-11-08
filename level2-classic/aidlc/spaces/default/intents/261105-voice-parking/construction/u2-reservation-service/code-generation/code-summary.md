# u2-reservation-service 코드 요약

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] code-generation-plan.md(Step 1~21, 결정 D1~D8), unit-test-instructions.md, functional-design/, nfr-requirements/, nfr-design/, contract-summary.md(C3, C4, C5)

## 만든 것

예약 상태를 확정하고(결제 대기 → 예약 완료 / 결제 실패 / 180초 자동 취소) 상태 변화를 SSE로 푸시하는 서비스다. Java 17, Spring Boot 3.5.7(Spring MVC), Maven. 포트 8082, 기본 바인딩은 루프백(`127.0.0.1`). 저장소는 이 서비스의 인메모리다.

| 위치 | 내용 |
|---|---|
| `pom.xml` (루트) | 모듈 `services/u2-reservation-service` 추가 |
| `services/u2-reservation-service/pom.xml` | Spring Boot web·validation·actuator, JaCoCo 라인 80% 하한 검사, `slow` 프로필 |
| `…/domain/` | `Reservation`(불변, 전이와 상태별 안내 문구), `ReservationStatus`, `AppliedEvent`, `Outcome`, `ApplyResult` |
| `…/reservation/` | `ReservationService`(생성·결과 반영·취소·조회), `ReservationStore`(인메모리, 예약별·세션별 잠금), `DeadlineTimers`, `SearchClient`/`HttpSearchClient`(u1 호출), `CreateCommand`, `PassInfo`, `ErrorCode`, `ReservationException` |
| `…/notification/` | `NotificationService`, `Connection`(연결별 FIFO 큐·단일 배출), `Subscription`, `Snapshot`, `SnapshotSink`, `EventChannel`/`SseChannel`, `SubscriptionLimitException`. 이 패키지는 `reservation`·`domain`을 참조하지 않는다 |
| `…/web/` | `ReservationController`(C3 조회), `EventsController`(SSE), `InternalReservationController`(C5), `ApiExceptionHandler`, `RequestLogFilter`, `SessionIds`, `dto/` |
| `…/config/` | `ReservationProperties`, `TimeConfig`(주입 시계), `PushConfig`, `SearchClientConfig` |
| `src/main/resources/application.properties` | 포트·바인딩·u1 주소·마감 시간·health만 노출 |
| `src/test/java/…` | 시험 약 145개와 지원 클래스(`support/`) |

## 실행 방법

저장소 루트에서:

```
mvn -q -pl services/u2-reservation-service test        # 일상 시험 (145개)
mvn -q -pl services/u2-reservation-service verify      # 시험 + JaCoCo 80% 하한
mvn -q -pl services/u2-reservation-service verify -Pslow   # 병합 전: 실제 180초 취소 시험 포함
mvn -pl services/u2-reservation-service spring-boot:run    # 서비스 기동 (포트 8082)
```

환경 변수: `SERVER_PORT`(기본 8082), `SERVER_ADDRESS`(기본 127.0.0.1), `SEARCH_SERVICE_URL`(기본 http://127.0.0.1:8081).

## 시험 결과

- 일상 실행 145개 통과, 실패·건너뜀 0. 라인 커버리지 96.6%(545/564), 하한 80% 검사 통과.
- 실제 180초 취소 시험(`RealDeadlineSlowTest`, `-Pslow`)은 별도 실행에서 통과했다(189.5초, 취소 시각이 178~182초 안). `verify -Pslow`를 한 번에 돌린 실행은 하지 않았다. 병합 전 검증으로 한 번 돌려야 한다.
- 성능(예열 20회 제외 100회 최댓값, 목표 대비): 연결 있는 푸시 19ms(150), 읽지 않는 클라이언트 1개 113ms(150), 4개 26ms(150), 동시 10 세션 28ms(150), 구독자 없는 결과 반영 84ms(100), 첫 SSE 이벤트 86ms(200), 예약 생성 29ms(500). 어느 목표도 완화하지 않았다.
- 구독자 없이 취소가 확정되는 경로는 마감을 2초로 줄인 실제 스케줄러 시험으로 확인했다.
- u1-search-service 시험 65개는 그대로 통과한다(u1 파일은 건드리지 않았다).
- Red 증거: 계층마다 시험을 먼저 썼고 첫 실행은 구현 클래스가 없어 컴파일 실패였다(`ReservationTest`, `ReservationStoreTest`, 예약 로직·알림·API 시험). 시험 자체의 오류 하나(구독 상한 32에 걸린 시험 설정)는 시험을 고쳤고 코드는 고치지 않았다.

## 실행하지 않은 시험

- 없음. 모든 시험을 이 단계에서 실행했다. 병합 전 검증으로 `verify -Pslow`를 한 번에 돌리는 실행만 남아 있다.

## 계획과 달라진 점

- `SearchClient`는 인터페이스이고 RestClient 기반 구현은 `HttpSearchClient`다(시험에서 가짜 u1을 쉽게 쓰려고).
- 계획 배치에 없던 파일: `notification/EventChannel`, `Connection`, `Subscription`, `SubscriptionLimitException`, `SseChannel`.
- 연결을 닫는 일은 별도 데몬 스레드에서 한다(D5: 막힌 쓰기 스레드의 반환에 의존하지 않음).
- `SnapshotSink.publish(snapshot, receivedNanos)`가 요청 수신 시각을 받는다(푸시 로그의 `ms`).
- 웹 시험 컨텍스트는 구독 상한을 500, 성능 시험은 5000으로 올려서 쓴다. 상한 시험은 2로 줄인다. 운영 기본값은 32다.
- 코드 생성 결정 D1~D8은 모두 계획대로 구현했다.

## 다른 유닛에 넘기는 사항

- **u3-payment-service**
  - 예약 생성(C5)의 멱등 재요청 200 응답의 `status`는 PENDING_PAYMENT가 아닐 수 있다(D1). 종료된 예약을 받았을 때 모의 PG에 다시 결제할지는 u3가 정한다.
  - 결제 결과 반영(C5)은 형식 오류에 400 INVALID_REQUEST를 돌려준다(D3).
  - `passId`, `idempotencyKey`, `eventId`는 100자 이하로 보낸다.
  - 이 서비스를 부르는 주소는 루프백 주소(`127.0.0.1`)로 둔다.
- **u4-web-ui**: 구독 연결이 상한(32)을 넘으면 503 `SUBSCRIPTION_LIMIT`이 온다. 일반 오류로 처리하고 잠시 뒤 다시 연결한다(D7). 종료 상태 스냅샷을 받으면 화면이 SSE 연결을 스스로 닫는다.
- **u5-local-run**: 이 서비스를 기본 설정(루프백 바인딩, 포트 8082)으로 띄우고 `SEARCH_SERVICE_URL`을 u1 주소로 준다. 병합 전 검증 스크립트에 `verify -Pslow`를 넣는다.
- 계약 요약(contract-summary.md)은 검토가 끝난 문서라 고치지 않았다. D1·D3·D7의 응답 추가는 계약 변경으로 다음 계약 갱신 때 반영해야 한다.

## 알려진 사항

- `services/u2-reservation-service/target/`은 `.gitignore`에 없다. `.gitignore`는 사람의 파일이라 건드리지 않았다.
- 인메모리 저장소라 재시작하면 예약이 모두 사라진다(설계된 동작).
- 설계 검토에서 남은 항목(막힌 쓰기 스레드, 503 재연결 정책 등)은 구현 결정 D5·D7로 닫았다.

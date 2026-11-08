# u2-reservation-service 단위 시험 안내

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] code-generation-plan.md, nfr-requirements/tech-stack-decisions.md(JUnit 5, AssertJ, Spring Boot Test, JaCoCo), nfr-design/performance-design.md(측정 방법), team.md(TDD, 80% 하한)

## 사전 준비

- Java 17, Maven 3.9 (이 머신에 설치됨). 의존성을 내려받으려면 네트워크 접근이 필요하다.
- Docker는 쓰지 않는다. 다른 서비스(u1)를 띄울 필요가 없다. u1 호출은 시험에서 가짜로 대체한다.

## 이 유닛의 시험 실행 명령

저장소 루트에서 실행한다. 이 유닛에만 한정된 명령이다. 일상 실행에서는 `slow`로 표시한 시험(실제 180초 취소)을 뺀다.

```
mvn -q -pl services/u2-reservation-service test
```

한 시험만 실행하려면 `-Dtest=<클래스명>`을 덧붙인다 (예: `-Dtest=ReservationServiceTest`).

## 병합 전 검증 실행

`slow` 시험을 포함해 시험과 커버리지 검사를 한 번에 실행한다. 180초 이상 걸린다.

```
mvn -q -pl services/u2-reservation-service verify -Pslow
```

## 커버리지

- 목표: 라인 커버리지 80% 이상 (팀 규칙 하한, 낮추지 않는다).
- JaCoCo 하한 검사는 시험이 모두 통과해도 하한 미달이면 종료 코드가 0이 아니게 만든다. 그래서 시험 명령과 따로 실행한다.

```
mvn -q -pl services/u2-reservation-service verify
```

- 프로젝트 전체 하한은 Build and Test가 전체 시험으로 확인한다. 이 유닛 단독 실행의 하한 결과는 참고용이며, 선택된 시험이 모두 통과하고 하한만 미달이면 시험 실패가 아니라 그 사실을 기록한다.

## 시험 구성 (약 35개)

| 시험 | 대상 | 확인하는 것 |
|---|---|---|
| ReservationTest | 도메인 | 초기 상태·version, 허용 전이 세 가지, 종료 상태 불변, 상태별 message·시각 필드, Snapshot 변환 |
| ReservationStoreTest | 저장소 | 예약·멱등 키·세션별 결제 대기·최근 예약 조회, 판정 기록, 서로 다른 키의 잠금이 막지 않음 |
| ReservationServiceCreateTest | 예약 생성 | 정상, 형식 오류, 멱등 재요청, 409, 같은 키 동시 2건, 실패 뒤 새 예약, u1 404·422·502 |
| ReservationServiceApplyTest | 결제 결과 | 승인·실패 반영, 같은 eventId 3회, 다른 eventId 두 번째 REJECTED, 179·180·181초 경계, 늦은 승인 비부활, 404 |
| DeadlineTimersTest | 자동 취소 | 타이머 콜백이 취소, 종료된 예약에는 무동작, 타이머 해제, 승인과 취소 경합 |
| SearchClientTest | u1 호출 | 350ms 시도·200ms 간격·1.5초 마감·최대 3회, 마감 안 실패 시 오류 |
| NotificationServiceTest | 알림 | version 규칙, 첫 이벤트 = 최신 스냅샷, 연달아 발행 시 단조 증가, 등록·발행 동시, 쓰기 실패 격리, 큐 16 초과, 쓰기 1초 초과, 구독 상한, 실행기 거절 |
| ReservationControllerTest | C3 조회 | current 200·404·401, 단건 200·404(다른 세션) |
| EventsControllerTest | C3 구독 | 첫 snapshot 이벤트(id = version), 401, 404, 503, 헤더 우선 |
| InternalReservationControllerTest | C5 | 생성 201·200·400·404·409·422·502, 결과 반영 200·400·404 |
| ApiExceptionHandlerTest | 오류 처리 | 500 INTERNAL_ERROR와 공통 형식, 본문에 입력 원문 없음 |
| RequestLogFilterTest | 요청·전이 로그 | 경로·상태·시간·세션 앞 8자, 쿼리 문자열 비기록, 전이·거부·중복 로그 |
| PerformanceTest | 성능 | 예열 20회 제외 100회 최댓값: 150ms/100ms/200ms/500ms, 느린 클라이언트 1~4개, 동시 10 세션, 마감 2초 실제 스케줄러 경로 |
| RealDeadlineSlowTest (`slow`) | 실제 180초 취소 | 178~182초 안에 취소 |
| NoSecretsTest | 비밀 값 | 소스·설정에 비밀 값 패턴 없음 |
| LoopbackBindingTest | 바인딩 | 기본 `server.address`가 루프백, 비루프백 주소 접속 거부(주소 없으면 건너뜀) |
| PackageBoundaryTest | 구조 | `notification` 패키지가 `reservation`·`domain`을 참조하지 않음 |
| HealthEndpointTest | 상태 확인 | `/actuator/health`가 UP |

## 모의·시험 데이터

- 시각: 주입하는 시험용 `Clock`을 쓰고 시계를 앞으로 돌려 경계를 검증한다. 타이머는 콜백을 직접 부르거나 마감을 2초로 줄여 실제 스케줄러 경로를 한 번 확인한다.
- u1 호출: 가짜 `SearchClient`(고정 응답, 404, 422, 지연, 실패)를 쓴다. `SearchClientTest`만 가짜 HTTP 서버로 재시도 타이밍을 확인한다.
- Notification: 서비스 시험에서는 기록용 `SnapshotSink`를 쓰고, 알림 시험에서는 쓰기를 느리게·실패하게 만드는 가짜 연결을 쓴다.
- 동시성 시험은 여러 스레드로 같은 요청을 동시에 보내고 결과를 여러 번 반복해 확인한다.

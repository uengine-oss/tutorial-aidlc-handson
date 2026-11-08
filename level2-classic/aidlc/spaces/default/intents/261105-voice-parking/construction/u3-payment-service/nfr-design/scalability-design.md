# u3-payment-service 확장성 설계

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] nfr-requirements/scalability-requirements.md(NFR4.1~NFR4.5), rules.md(BR2.2, BR3.4, BR5.1), entities.md, components.md(Payment 경계, ADR-005)

| 설계 | 방식 | 충족하는 요구사항 |
|---|---|---|
| 세분화된 잠금 | 이벤트 ID별, (세션, 멱등 키)별, Payment별 잠금을 `ConcurrentHashMap`으로 관리한다. 전역 잠금은 없다. 서로 다른 이벤트·키·결제의 처리는 서로를 막지 않는다. | NFR4.2 |
| 데모 규모 스레드 | 서블릿 스레드 풀은 기본값, 모의 PG 스케줄러 스레드 1개, 모의 PG 발송 실행기 스레드 4개(큐 상한 64, 넘으면 호출 쪽이 오류 로그를 남기고 그 알림을 포기). 동시 10 세션 시험으로 목표를 확인한다. | NFR4.1, NFR4.5 |
| 단일 프로세스 | 상태가 프로세스 메모리에 있으므로 서비스는 한 개만 띄운다. 포트 8083 고정으로 두 번째 인스턴스는 기동에 실패한다. | NFR4.3 |
| 패키지 경계 | 패키지를 `payment`(Payment·웹훅 로직), `webhook`·`web`(웹 계층), `pg`(모의 PG)로 나눈다. `PaymentGateway` 인터페이스는 `payment` 패키지에 두고 `pg`가 이를 구현한다(의존 방향 `pg` → `payment`). 요청 값 `PgPayment`(paymentId, mockScenario, mockDelaySeconds)도 `payment`에 두며 이 값이 시나리오와 지연을 운반한다. `payment`·`web`은 `pg`를 참조하지 않는다. 서명 계산은 별도 `signature` 패키지(`HmacSigner`)를 `web`의 서명 검증기와 `pg`가 함께 쓴다. 의존 주입 설정(`config`)만 `pg`의 구현을 `PaymentGateway`에 잇는다. `pg`와 그 설정을 지워도 나머지가 빌드된다. 의존 방향 시험은 추가 의존성 없이 `payment`·`web`·`signature` 소스의 import 문에 `pg`가 없는지 소스 파일을 훑어 확인하는 시험으로 한다. | NFR4.4 |

## 확장하지 않는 것

- 여러 인스턴스, 영속 저장소, 끝난 결제 기록 정리는 설계하지 않는다(NFR4.3, 범위 밖). 확장이 필요하면 저장소를 공유 저장소로, 모의 PG 알림을 외부 큐로 옮기는 별도 변경이다.

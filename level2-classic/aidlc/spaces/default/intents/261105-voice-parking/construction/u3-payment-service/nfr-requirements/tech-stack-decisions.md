# u3-payment-service 기술 스택 결정

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] team.md(백엔드 Java Spring Boot, Java는 IDE 기본 설정, TDD, 커버리지 80%, Docker 금지, 로컬 실행 스크립트), requirements.md의 NFR4, NFR6, NFR7, NFR8, NFR9, performance-requirements.md, reliability-requirements.md, u2-reservation-service의 tech-stack-decisions.md(같은 도구·버전 방침)

도구는 u1, u2와 같다. 이 머신에서 확인한 도구: Java 17, Maven 3.9.12, Node 25.2.1 / npm 11.6.2.

| 영역 | 선택 | 이유 | 요구사항 |
|---|---|---|---|
| 언어 | Java 17 | 이 머신에 설치되어 있고 Spring Boot 3가 요구하는 최소 버전이다 | NFR4 |
| 프레임워크 | Spring Boot 3.x (Spring MVC, validation, actuator) | 팀 규칙. 웹 요청 처리와 상태 확인 경로(NFR1.7) 기본 제공 | NFR4 |
| 서명 | JDK `javax.crypto.Mac`(HmacSHA256), `MessageDigest.isEqual`로 상수 시간 비교 | 추가 의존성이 필요 없고 상수 시간 비교를 표준으로 제공한다 | NFR6.1 |
| 웹훅 본문 처리 | 서명 검증을 위해 요청 본문 원문 바이트를 먼저 읽고(8KB 상한) 그 바이트로 서명 검증 뒤 JSON으로 해석 | 해석 전 원문에 서명하기 때문. 본문이 재직렬화되면 서명이 달라진다 | NFR6.1, NFR6.5 |
| 모의 PG | 같은 프로세스의 별도 패키지. `Clock`과 스케줄러(`ScheduledExecutorService`)로 지연 알림을 예약하고 `RestClient`로 웹훅 주소를 실제 HTTP로 호출 | 외부 PG처럼 동작하되 실제 PG로 바꿀 때 이 패키지만 걷어낸다. 시계 주입으로 지연을 대기 없이 시험한다 | NFR4.4, NFR5.11 |
| 서비스 간 호출 | Spring의 `RestClient`(u2 예약 생성·결과 반영). 예약 생성은 1.5초 제한·재시도 없음, 결과 반영은 2초 제한·200ms 간격 최대 2회 재시도를 직접 짠 반복으로 구현 | 재시도 라이브러리를 추가하지 않는다. 결과 반영은 같은 eventId라 재시도해도 안전하다 | NFR5.3, NFR5.4 |
| 동시성 제어 | 이벤트 ID별, (세션, 멱등 키)별 잠금(`ConcurrentHashMap`으로 잠금 객체 관리) | 서로 다른 키가 서로 막지 않으면서 같은 키를 직렬화한다. 전역 잠금은 쓰지 않는다 | NFR4.2, NFR5.1, NFR5.5 |
| 저장소 | 이 서비스 프로세스 메모리 (외부 DB 없음, 다른 서비스와 공유하지 않음) | 데모 규모이고 Docker가 금지다. 재시작 시 상태를 잃는 것을 받아들인다(NFR5.9) | NFR8 |
| 시험 | JUnit 5, AssertJ, Spring Boot Test(MockMvc), u2 호출은 가짜로 대체, 모의 PG 자기 호출은 시험용 웹훅 수신 서버로 확인 | TDD와 계약 시험에 충분하다 | NFR7 |
| 커버리지 | JaCoCo, 라인 커버리지 80% 하한 | 팀 규칙. 빌드에서 하한 미달이면 실패 | NFR7 |
| 포매터·린터 | 별도 도구 없음 (IDE 기본) | 팀 규칙(Java는 IDE 기본) | NFR9 |
| 로깅 | Spring Boot 기본 로깅(SLF4J) | 추가 의존성 없이 NFR1.8, NFR1.9, NFR5.12~NFR5.14를 만족한다 | NFR1, NFR5 |
| 빌드 | Maven (서비스마다 독립 프로젝트, 저장소 루트에 상위 pom) | 이 머신에 설치. 서비스를 따로 만들고 시험할 수 있다 | NFR4, NFR8 |
| 실행 | 서비스별 `mvn spring-boot:run` 같은 실행 명령을 u5-local-run이 한곳에서 묶는다. 이 서비스의 포트는 8083. 환경 변수 `PAYMENT_WEBHOOK_SECRET`(필수), `RESERVATION_SERVICE_URL`(기본 http://127.0.0.1:8082), `WEBHOOK_URL`(모의 PG가 부르는 자기 웹훅 주소, 기본 http://127.0.0.1:8083/api/payments/webhook) | 팀 규칙(로컬 실행 스크립트, Docker 금지) | NFR8 |

## 요구사항

| ID | 요구사항 | 측정 방법 | 출처 |
|---|---|---|---|
| NFR7.1 | 각 계층마다 실패하는 테스트를 먼저 쓴 뒤 구현한다(TDD). 라인 커버리지는 JaCoCo로 측정해 80% 이상이어야 하며 하한 미달이면 빌드가 실패한다. 하한은 약화하지 않는다. 병합 전에 시험과 JaCoCo 검사(`mvn verify`)를 실행한다. | 빌드의 JaCoCo 검사 통과 | NFR7, team.md Testing Posture |
| NFR9.1 | Java는 IDE 기본 설정을 쓰고 별도 포매터·린터를 추가하지 않는다. 이름은 Java 표준 관례(클래스 PascalCase, 메서드·변수 camelCase)를 따른다. | 코드 검토 | NFR9, team.md Code Style |
| NFR8.2 | Docker를 쓰지 않는다. 서비스는 실행 명령 한 줄로 로컬에서 기동되며 u2 주소, 웹훅 주소, 포트는 환경 변수로 바꿀 수 있다. | 실행 확인 | NFR8, project.md Forbidden |

## 대안

- 서명에 Spring Security를 쓰는 안: 인증·세션 등 쓰지 않는 기능을 끌어들이므로 선택하지 않았다.
- 모의 PG를 별도 프로세스(서비스)로 두는 안: 사용자가 모의 PG를 payment-service 안에 두기로 했다(units-generation Q2).
- Resilience4j 등 재시도 라이브러리: 재시도가 한 곳이라 직접 반복으로 충분해 선택하지 않았다.

## Assumptions & Open Questions

- [assumption] Spring Boot 3.x의 구체적 버전은 코드 생성 단계에서 u1·u2와 같은 상위 pom의 버전(3.5.7)을 쓴다. 필요한 의존성을 내려받을 수 있는 네트워크 접근이 있다고 가정한다.
- [assumption] 인메모리 저장소와 재시작 시 상태 소실은 사용자 확인 전까지 가정이다.
- 열린 질문: 없음.

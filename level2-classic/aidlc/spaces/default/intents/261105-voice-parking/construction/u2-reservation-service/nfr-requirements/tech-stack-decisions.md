# u2-reservation-service 기술 스택 결정

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] team.md(백엔드 Java Spring Boot, Java는 IDE 기본 설정, TDD, 커버리지 80%, Docker 금지, 로컬 실행 스크립트), requirements.md의 NFR4, NFR7, NFR8, NFR9, performance-requirements.md, reliability-requirements.md, contract-summary.md(포트 8082, 제한 시간·재시도), u1-search-service의 tech-stack-decisions.md(같은 도구·버전 방침)

도구는 u1-search-service와 같다. 이 머신에서 확인한 도구: Java 17, Maven 3.9.12, Node 25.2.1 / npm 11.6.2. Gradle은 없다.

| 영역 | 선택 | 이유 | 요구사항 |
|---|---|---|---|
| 언어 | Java 17 | 이 머신에 설치되어 있고 Spring Boot 3가 요구하는 최소 버전이다 | NFR4 |
| 프레임워크 | Spring Boot 3.x (Spring MVC, validation, actuator) | 팀 규칙. 웹 요청 처리, SSE(`SseEmitter`), 상태 확인 경로(NFR1.5)를 기본 제공 | NFR4 |
| 실시간 푸시 | Spring MVC의 `SseEmitter` | 구독자 수가 데모 규모라 스레드 기반 SSE로 충분하고, WebFlux를 추가하면 의존성과 시험 방식이 늘어난다 | NFR1 |
| 타이머 | 주입한 `Clock`과 `TaskScheduler`(단일 스레드 이상의 스케줄러)로 마감 시각에 한 번 실행 | 시험에서 시계를 주입해 179·180·181초 경계를 대기 없이 검증하기 위해(NFR2.4) | NFR2 |
| 동시성 제어 | 예약 ID별, 세션 ID별 잠금(`ConcurrentHashMap`으로 잠금 객체 관리) | 서로 다른 예약·세션이 서로 막지 않으면서 같은 예약 판정을 직렬화한다(NFR4.2, NFR5.2). 전역 잠금은 쓰지 않는다 | NFR5 |
| 저장소 | 이 서비스 프로세스 메모리 (외부 DB 없음, 다른 서비스와 공유하지 않음) | 데모 규모이고 Docker가 금지다. 재시작 시 상태를 잃는 것을 받아들인다(NFR5.7) | NFR8 |
| 서비스 간 호출 | Spring의 `RestClient`(u1 주차권 조회). 제한 시간 2초, 200ms 간격 최대 2회 재시도는 직접 짠 반복으로 구현 | 재시도 라이브러리를 추가하지 않는다. 읽기 전용 조회만 재시도한다 | NFR5, NFR8 |
| 시험 | JUnit 5, AssertJ, Spring Boot Test(MockMvc), u1 호출은 가짜 서버로 대체 | TDD와 계약 시험에 충분하다. 시계 주입과 경합 시험은 단위 시험으로 한다 | NFR7 |
| 커버리지 | JaCoCo, 라인 커버리지 80% 하한 | 팀 규칙. 빌드에서 하한 미달이면 실패 | NFR7 |
| 포매터·린터 | 별도 도구 없음 (IDE 기본) | 팀 규칙(Java는 IDE 기본) | NFR9 |
| 로깅 | Spring Boot 기본 로깅(SLF4J) | 추가 의존성 없이 NFR1.6~NFR1.8, NFR5.9, NFR5.10을 만족한다 | NFR1, NFR5 |
| 빌드 | Maven (서비스마다 독립 프로젝트, 저장소 루트에 상위 pom) | 이 머신에 설치. 서비스를 따로 만들고 시험할 수 있다 | NFR4, NFR8 |
| 실행 | 서비스별 `mvn spring-boot:run` 같은 실행 명령을 u5-local-run이 한곳에서 묶는다. 이 서비스의 포트는 8082, u1 주소는 환경 변수로 주입 | 팀 규칙(로컬 실행 스크립트, Docker 금지) | NFR8 |

## 요구사항

| ID | 요구사항 | 측정 방법 | 출처 |
|---|---|---|---|
| NFR7.1 | 각 계층마다 실패하는 테스트를 먼저 쓴 뒤 구현한다(TDD). 라인 커버리지는 JaCoCo로 측정해 80% 이상이어야 하며 하한 미달이면 빌드가 실패한다. 하한은 약화하지 않는다. | 빌드의 JaCoCo 검사 통과 | NFR7, team.md Testing Posture |
| NFR9.1 | Java는 IDE 기본 설정을 쓰고 별도 포매터·린터를 추가하지 않는다. 이름은 Java 표준 관례(클래스 PascalCase, 메서드·변수 camelCase)를 따른다. | 코드 검토 | NFR9, team.md Code Style |
| NFR8.2 | Docker를 쓰지 않는다. 서비스는 실행 명령 한 줄로 로컬에서 기동되며 u1 주소와 포트는 환경 변수로 바꿀 수 있다. | 실행 확인 | NFR8, project.md Forbidden |
| NFR4.5 | Reservation과 Notification은 같은 프로세스에 두되 패키지 경계를 나눈다. Notification은 Reservation을 호출하지 않으며 Reservation이 스냅샷을 밀어 넣는다. | 패키지 의존 방향 시험(Notification 패키지가 Reservation 패키지를 참조하지 않음) | NFR4, components.md |

## 대안

- WebFlux 기반 SSE: 스레드 기반으로 충분한 규모이고 시험 방식이 더 복잡해져 선택하지 않았다.
- 예약 저장소를 H2 내장 DB로: 재시작 복구는 가능해지지만 설정·기동 시간이 늘고 프로토타입에서 영속성이 요구되지 않아 선택하지 않았다.
- Resilience4j 등 재시도 라이브러리: 재시도가 읽기 전용 조회 한 곳이라 직접 반복으로 충분해 선택하지 않았다.

## Assumptions & Open Questions

- [assumption] Spring Boot 3.x의 구체적 버전은 코드 생성 단계에서 사용 가능한 최신 안정 버전으로 정한다. 필요한 의존성을 내려받을 수 있는 네트워크 접근이 있다고 가정한다.
- [assumption] 인메모리 저장소와 재시작 시 상태 소실은 사용자 확인 전까지 가정이다. 영속성이 필요하면 H2 같은 내장 DB를 추가하는 변경이 이 단계 결정을 바꾼다.
- 열린 질문: 없음.

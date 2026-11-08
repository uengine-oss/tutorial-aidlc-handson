# u1-search-service 기술 스택 결정

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] team.md(백엔드 Java Spring Boot, Java는 IDE 기본 설정, TDD, 커버리지 80%, Docker 금지, 로컬 실행 스크립트), requirements.md의 NFR4, NFR7, NFR8, NFR9, performance-requirements.md

이 머신에서 확인한 도구: Java 17 (OpenJDK Zulu 17.0.17), Maven 3.9.12, Node 25.2.1 / npm 11.6.2. Gradle은 없다.

| 영역 | 선택 | 이유 | 요구사항 |
|---|---|---|---|
| 언어 | Java 17 | 이 머신에 설치되어 있고 Spring Boot 3가 요구하는 최소 버전이다 | NFR4 |
| 프레임워크 | Spring Boot 3.x (web, validation, actuator) | 팀 규칙. 웹 요청 처리와 상태 확인 경로(NFR3.4)를 기본 제공 | NFR4 |
| 빌드 | Maven (서비스마다 독립 프로젝트, 저장소 루트에 상위 pom) | 이 머신에 설치. 서비스를 따로 만들고 시험할 수 있다 | NFR4, NFR8 |
| 저장소 | 메모리 안의 목업 데이터 (외부 DB 없음). 시작할 때 `data` 폴더의 JSON을 읽어 적재 | 읽기 전용 7건이라 DB가 필요 없고 Docker도 금지다 | NFR3.3, NFR8 |
| 시험 | JUnit 5, AssertJ, Spring Boot Test | TDD와 계약 시험에 충분하다 | NFR7 |
| 커버리지 | JaCoCo, 라인 커버리지 80% 하한 | 팀 규칙. 빌드에서 하한 미달이면 실패 | NFR7 |
| 포매터·린터 | 별도 도구 없음 (IDE 기본) | 팀 규칙(Java는 IDE 기본) | NFR9 |
| 로깅 | Spring Boot 기본 로깅(SLF4J) | 추가 의존성 없이 NFR3.5를 만족한다 | NFR3.5 |
| 실행 | 서비스별 `mvn spring-boot:run` 같은 실행 명령을 u5-local-run이 한곳에서 묶는다. 이 서비스의 포트는 8081 | 팀 규칙(로컬 실행 스크립트, Docker 금지) | NFR8 |

## 요구사항

| ID | 요구사항 | 측정 방법 | 출처 |
|---|---|---|---|
| NFR7.1 | 각 계층마다 실패하는 테스트를 먼저 쓴 뒤 구현한다(TDD). 라인 커버리지는 JaCoCo로 측정해 80% 이상이어야 하며 하한 미달이면 빌드가 실패한다. 하한은 약화하지 않는다. | 빌드의 JaCoCo 검사 통과 | NFR7, team.md Testing Posture |
| NFR9.1 | Java는 IDE 기본 설정을 쓰고 별도 포매터·린터를 추가하지 않는다. 이름은 Java 표준 관례(클래스 PascalCase, 메서드·변수 camelCase)를 따른다. | 코드 검토 | NFR9, team.md Code Style |

## 대안

- Gradle: 이 머신에 없어 설치가 더 필요하므로 선택하지 않았다.
- 목업 데이터를 내장 DB(H2)에 넣기: 읽기 전용 7건에는 과하고 기동 시간과 설정이 늘어서 선택하지 않았다.

## Assumptions & Open Questions

- [assumption] Spring Boot 3.x의 구체적 버전은 코드 생성 단계에서 사용 가능한 최신 안정 버전으로 정한다. 필요한 의존성을 내려받을 수 있는 네트워크 접근이 있다고 가정한다.
- 열린 질문: 없음.

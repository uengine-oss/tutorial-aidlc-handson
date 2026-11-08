# u1-search-service 단위 시험 안내

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] code-generation-plan.md, nfr-requirements/tech-stack-decisions.md(JUnit 5, AssertJ, Spring Boot Test, JaCoCo), team.md(TDD, 80% 하한)

## 사전 준비

- Java 17, Maven 3.9 (이 머신에 설치됨). 의존성을 내려받으려면 네트워크 접근이 필요하다.
- Docker는 쓰지 않는다. 별도 서비스를 띄울 필요가 없다.

## 이 유닛의 시험 실행 명령

저장소 루트에서 실행한다. 이 유닛에만 한정된 명령이다.

```
mvn -q -pl services/u1-search-service test
```

한 시험만 실행하려면 `-Dtest=<클래스명>`을 덧붙인다 (예: `-Dtest=RecommendationServiceTest`).

## 커버리지

- 목표: 라인 커버리지 80% 이상 (팀 규칙 하한, 낮추지 않는다).
- JaCoCo 하한 검사는 시험이 모두 통과해도 하한 미달이면 종료 코드가 0이 아니게 만든다. 그래서 시험 명령과 따로 실행한다.

```
mvn -q -pl services/u1-search-service verify
```

- 프로젝트 전체 하한은 Build and Test가 전체 시험으로 확인한다. 이 유닛 단독 실행의 하한 결과는 참고용이며, 선택된 시험이 모두 통과하고 하한만 미달이면 시험 실패가 아니라 그 사실을 기록한다.

## 시험 구성 (약 16개)

| 시험 | 대상 | 확인하는 것 |
|---|---|---|
| MockDataLoaderTest | 데이터 적재 | 정상 7건 적재, 참조 위반·중복 ID·필수 누락이면 기동 거부 |
| ParkingRepositoryTest | 저장소 | 장소별 주차장, passId 조회, 없는 passId |
| QueryParserTest | 입력 해석 | 장소·시간 추출, 장소 없음, 시간 없음, 범위 밖 시간 |
| RecommendationServiceTest | 추천 | 시험 데이터로 62·60·50 순위, 92·72·0 점수 예시, 시간 못 채우는 주차권 제외, 후보 없음, 후보 2개뿐, 동점 규칙, topFactor, 후보 1개 100점 |
| PassLookupServiceTest | 주차권 조회 | 가격 = 시간당 요금 × 시간, 404, 422 |
| SearchControllerTest | C1 웹 계층 | 200, 422, 400, 후보 없음 200 |
| InternalPassControllerTest | C4 웹 계층 | 200, 404, 422, 400 |
| ApiExceptionHandlerTest | 오류 처리 | 500 INTERNAL_ERROR와 공통 형식, 본문에 입력 원문 없음 |
| RequestLogFilterTest | 요청 로그 | 경로·상태·시간·세션 앞 8자, 입력 원문 비기록 |
| PerformanceTest | 성능 | 100회 반복 최대 2초(추천)·500ms(조회), 동시 10건 |
| NoSecretsTest | 비밀 값 | 소스·설정에 비밀 값 패턴 없음 |

## 모의·시험 데이터

- 단위 시험은 `src/test/resources`의 시험 전용 JSON 또는 메모리에서 만든 데이터를 쓴다. 실제 `parking-data.json`을 쓰는 시험은 계산 예시 표와 정확히 같은 값을 기대한다.
- 외부 서비스가 없어 모의 객체(mock)가 필요한 곳이 없다.

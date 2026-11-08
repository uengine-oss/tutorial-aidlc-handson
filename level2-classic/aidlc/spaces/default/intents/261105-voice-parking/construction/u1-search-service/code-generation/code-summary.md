# u1-search-service 코드 요약

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] code-generation-plan.md(승인된 계획), unit-test-instructions.md, functional-spec.md, rules.md, nfr-design/, contract-summary.md(C1, C4)

## 만든 파일

저장소 루트에 만들었다 (`target/` 빌드 산출물 제외).

| 영역 | 파일 |
|---|---|
| 빌드 | `pom.xml`(상위, 모듈 `services/u1-search-service`), `services/u1-search-service/pom.xml` |
| 설정·데이터 | `src/main/resources/application.properties`, `src/main/resources/data/parking-data.json`(목업 7건) |
| 도메인 | `domain/` ParkingLot, ParkingPass, ParsedQuery, ScoredPass, Factor |
| 데이터 | `data/` MockDataLoader, ParkingData, DataLoadException, ParkingRepository, DataConfig |
| 서비스 | `service/` QueryParser, RecommendationService, PassLookupService, ReasonFormatter, Ratio, ErrorCode, SearchException, PassInfo, DurationLimits, ServiceConfig |
| 웹 | `web/` SearchController, InternalPassController, ApiExceptionHandler, RequestLogFilter, `web/dto/` ApiError, PassResponse, SearchRequest, SearchResponse |
| 시험 | `src/test/java/kr/voiceparking/search/` 파일 12개 (아래 표) |

패키지는 `kr.voiceparking.search`. 서비스는 Java 17, Spring Boot 3.5.7, Maven으로 만들었고 포트는 `${SERVER_PORT:8081}`이다.

## 실행 방법

- 시험: 저장소 루트에서 `mvn -q -pl services/u1-search-service test`
- 커버리지 하한 검사까지: `mvn -q -pl services/u1-search-service verify`
- 서비스 실행: `mvn -pl services/u1-search-service spring-boot:run` (실행 스크립트로 묶는 일은 u5-local-run이 한다)

## 핵심 구현 결정

- 점수는 정수 분수(`Ratio`)로 계산해 0.5에서 올림한다. 부동소수점 오차가 없다.
- topFactor는 가중 기여도의 정확한 값으로 정하고 같으면 PRICE, DISTANCE, TIME_FIT 순으로 앞선 것을 쓴다.
- 이용 시간이 0이거나 24를 넘으면 장소 추출 단계에서 MISSING_DURATION이다 (BR1.2).
- 주차권 조회(C4)는 durationHours 1~24만 받고 아래 범위나 정수가 아니면 400 INVALID_DURATION이다. 검증은 passId 조회보다 먼저 한다.
- 오류 처리기가 Spring의 기본 오류(405, 415, 404)도 공통 형식 `{code, message}`로 바꾼다. 처리하지 못한 예외는 500 INTERNAL_ERROR(고정 문구)이고 입력 원문은 되돌리지 않는다.
- 요청 로그는 경로, 상태, 처리 시간, 세션 ID 앞 8자(유효한 UUID일 때만), 오류 코드를 남기고 본문은 남기지 않는다.
- 데이터는 시작할 때 적재하고 검증하며 하나라도 어긋나면 기동을 거부한다.

## 시험 결과 (이 단계에서 직접 확인)

| 항목 | 결과 |
|---|---|
| `mvn -q -pl services/u1-search-service verify` | 통과 (종료 코드 0) |
| 시험 수 | 65개 실행, 실패 0, 오류 0, 건너뜀 0 |
| 라인 커버리지(JaCoCo) | 95.8% (313줄 중 300줄), 하한 80% 통과. 하한 설정은 바꾸지 않았다 |

| 시험 | 대상 |
|---|---|
| MockDataLoaderTest, ParkingRepositoryTest | 데이터 적재와 저장소 |
| QueryParserTest | 입력 해석 |
| RecommendationServiceTest | 추천과 점수 |
| PassLookupServiceTest | 주차권 조회 |
| SearchControllerTest, InternalPassControllerTest | 웹 계층(C1, C4) |
| ApiExceptionHandlerTest, RequestLogFilterTest | 오류 처리, 요청 로그 |
| PerformanceTest | 100회 반복, 동시 10건 |
| NoSecretsTest, HealthEndpointTest | 비밀 값 패턴, 상태 확인 경로(health만 노출) |

## 이 단계에서 실행하지 않은 시험

- 없음. 모든 시험을 로컬에서 실행했다. 통합 시험(다른 서비스와의 연동)은 해당 서비스가 만들어진 뒤 Build and Test에서 다룬다.

## 계획과 다른 점

- 계획의 시험 수는 약 16개였으나 시험 표준(영역마다 오류·경계 사례 최소 둘)을 지키느라 65개가 되었다.
- 성능 시험의 100회 반복 측정값은 시험 통과 여부로만 확인했고 수치를 기록하지 않았다.
- 기능 설계 검토에서 나온 계약 불일치(C1의 400 INVALID_TEXT·400 MISSING_SESSION, C4의 400 INVALID_DURATION과 상한 24)가 코드에는 반영되었으나 `contract-summary.md`는 아직 고치지 않았다. 후속 유닛(u2)이 C4를 소비하기 전에 계약 문서를 맞춰야 한다.

## 후속 작업

- `.gitignore`에 `target/` 항목이 없다. 이 파일은 사용자의 것이라 이 단계에서 바꾸지 않았다. 빌드 산출물이 변경 목록에 섞이지 않게 하려면 추가가 필요하다.

# u1-search-service 코드 생성 계획

## Summary

- Builds: 음성 텍스트를 해석해 목업 주차권 중 최적 1개와 비교 후보 2개를 추천하는 검색 서비스(Java 17, Spring Boot 3, Maven)와 주차권 조회 기능
- Touches: 저장소 루트의 `pom.xml`과 `services/u1-search-service/` (소스, 목업 데이터 JSON, 설정, 시험)
- Tests: 단위·웹 계층 시험 약 16개를 TDD(실패하는 시험 먼저)로 작성하고 JaCoCo 80% 하한을 빌드에 건다

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] functional-spec.md, rules.md(BR1.1~BR4.2), entities.md(목업 7건), nfr-design/(performance, security, reliability, observability), nfr-requirements/tech-stack-decisions.md, contract-summary.md(C1, C4), unit-of-work.md(U1), team.md(TDD, 80% 하한, Java IDE 기본, Docker 금지)

## 코드 배치

저장소 루트(작업 공간)에 둔다. 이 유닛의 `source-manifest.json`에 만든 파일을 모두 적는다.

```
pom.xml                                   상위 pom (modules: services/u1-search-service)
services/u1-search-service/pom.xml
services/u1-search-service/src/main/java/kr/voiceparking/search/
    SearchApplication.java
    domain/ (ParkingLot, ParkingPass, ParsedQuery, ScoredPass)
    data/ (ParkingRepository, MockDataLoader)
    service/ (QueryParser, RecommendationService, PassLookupService, ReasonFormatter)
    web/ (SearchController, InternalPassController, ApiExceptionHandler, RequestLogFilter, dto 레코드)
services/u1-search-service/src/main/resources/ (application.properties, data/parking-data.json)
services/u1-search-service/src/test/java/kr/voiceparking/search/ (시험)
```

## Steps

- [x] Step 1: 프로젝트 골격과 운영 설정. 상위 `pom.xml`과 서비스 `pom.xml`(Spring Boot 3, Java 17, web, validation, actuator, 시험 의존성, JaCoCo 라인 커버리지 80% 하한 검사), `application.properties`(포트 `${SERVER_PORT:8081}`, actuator는 health만 노출). (NFR4.x, NFR7.1, NFR3.4)
- [x] Step 2: 시험 실행 준비. 서비스 단위 명령 `mvn -q -pl services/u1-search-service test`가 실행되는지 확인하고 `unit-test-instructions.md`에 기록한다. (NFR7.1)
- [x] Step 3: 데이터 모델 - Red. 목업 데이터 적재 시험을 먼저 쓰고 실패 출력을 기록한다: 정상 적재 7건, 참조 무결성 위반·중복 ID·필수 필드 누락이면 기동 거부. (NFR2.1)
- [x] Step 4: 데이터 모델 - Green. 도메인 레코드(ParkingLot, ParkingPass)와 `MockDataLoader`, `data/parking-data.json`(entities.md의 7건)을 구현한다.
- [x] Step 5: 데이터 모델 - Refactor. 시험이 통과한 채로 검증 코드를 정리한다.
- [x] Step 6: 저장소 - Red. `ParkingRepository` 시험: 장소별 주차장 조회, passId 조회, 없는 passId. (BR2.1, BR4.2)
- [x] Step 7: 저장소 - Green. 장소별 색인과 passId 해시맵을 만든다. (NFR3.3)
- [x] Step 8: 저장소 - Refactor.
- [x] Step 9: 비즈니스 로직 - Red. 시험을 먼저 쓴다: QueryParser(장소·시간 추출, 장소 없음, 시간 없음, 범위 밖 시간), 점수 계산(AC2.2.1의 92·72·0 입력 예시, 계산 예시 표의 62·60·50·40과 순위, 후보 1개이거나 값이 같으면 100점), 후보 제외(시간 못 채움), 후보 없음, 3개 제한과 후보 2개뿐일 때, 동점 규칙, 추천 이유의 topFactor, 주차권 조회(가격=시간당 요금×시간, 404, 422). (BR1.1~BR1.4, BR2.1~BR2.3, BR3.1~BR3.5, BR4.1, BR4.2)
- [x] Step 10: 비즈니스 로직 - Green. `QueryParser`, `RecommendationService`, `ReasonFormatter`, `PassLookupService`를 구현한다. 점수는 정수 비율로 계산하고 0.5에서 올림한다.
- [x] Step 11: 비즈니스 로직 - Refactor.
- [x] Step 12: API 계층 - Red. MockMvc 시험을 먼저 쓴다: C1 정상 200, 장소 없음 422, 시간 없음 422, 빈 text 400, 세션 헤더 형식 오류 400, 후보 없음 200 빈 배열; C4 정상 200, 없는 passId 404, 시간 못 채움 422, 잘못된 durationHours 400; 처리 못한 예외는 500 INTERNAL_ERROR. (BR1.5, BR1.6, BR4.2, NFR2.2, NFR6.2)
- [x] Step 13: API 계층 - Green. `SearchController`(`POST /api/search/recommendations`), `InternalPassController`(`GET /internal/passes/{passId}`), `ApiExceptionHandler`(공통 오류 형식 `{code, message}`), 요청 로그 필터를 구현한다. 오류 본문에 입력 원문을 넣지 않는다. (NFR6.3, NFR3.5, NFR3.6)
- [x] Step 14: API 계층 - Refactor.
- [x] Step 15: 성능과 보안 확인 시험. 100회 반복 응답 최대 2초(추천)와 500ms(주차권 조회), 동시 요청 10건, 소스·설정에 비밀 값 패턴이 없음을 확인하는 시험을 쓰고 통과시킨다. (NFR3.1, NFR3.2, NFR4.1, NFR6.1)
- [x] Step 16: 프론트엔드 동작. 이 유닛은 서비스라서 해당 없음.
- [x] Step 17: 환경·빌드 설정. 커버리지 하한을 `mvn verify`에서 검사하도록 확인한다. 하한은 낮추지 않는다. (NFR7.1)
- [ ] Step 18: 문서와 추적성. `code-summary.md`(만든 파일, 실행 방법, 기술 선택), `traceability.json`, `source-manifest.json`을 쓴다.

## 스토리 추적

| 스토리·인수 조건 | 계획 단계 |
|---|---|
| US1.1 AC1.1.1~AC1.1.3 | Step 9, 10, 12, 13 |
| US1.1 AC1.1.4 | 이 유닛 범위 밖(u2, u4가 맡음). 헤더 형식 확인만 Step 12, 13 |
| US2.1 AC2.1.1~AC2.1.3 | Step 6, 7, 9, 10 |
| US2.2 AC2.2.1~AC2.2.6 | Step 9, 10, 12, 13, 15 |
| 계약 C4(US3.1이 사용) | Step 9, 10, 12, 13 |

## Testing Contract

```json
{
  "version": 1,
  "methodology": "tdd",
  "source": "team",
  "ordering": "각 계층마다 실패하는 테스트를 먼저 작성한 뒤 그 테스트를 통과시키는 구현을 작성한다.",
  "scope": "classic",
  "test_strategy": "minimal",
  "project_type": "greenfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: tdd\n- **Ordering**: 각 계층마다 실패하는 테스트를 먼저 작성한 뒤 그 테스트를 통과시키는 구현을 작성한다.\n- `classic` 범위의 하한을 유지한다: 80% 라인 커버리지.\n- `classic` 범위의 하한을 유지한다: 병합 전 CI 실행. 하한은 약화할 수 없다."
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "tdd",
    "runner_step": "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - Red: write the failing tests and record the failing command output.",
      "Data model / database behavior - Green: implement only enough behavior to pass.",
      "Data model / database behavior - Refactor: improve the implementation while tests stay green.",
      "Repository / data access - Red: write the failing tests and record the failing command output.",
      "Repository / data access - Green: implement only enough behavior to pass.",
      "Repository / data access - Refactor: improve the implementation while tests stay green.",
      "Business logic - Red: write the failing tests and record the failing command output.",
      "Business logic - Green: implement only enough behavior to pass.",
      "Business logic - Refactor: improve the implementation while tests stay green.",
      "API / endpoint - Red: write the failing tests and record the failing command output.",
      "API / endpoint - Green: implement only enough behavior to pass.",
      "API / endpoint - Refactor: improve the implementation while tests stay green.",
      "Frontend behavior - Red: write the failing tests and record the failing command output.",
      "Frontend behavior - Green: implement only enough behavior to pass.",
      "Frontend behavior - Refactor: improve the implementation while tests stay green.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:569437ce643a91befd11b274b820beb938712e1b20ec466c3049ce509903f102",
  "contract_sha256": "sha256:296ad33f6f8e235b5c42f80437adace229248af280a22e4ef5ab043c7658107c"
}
```

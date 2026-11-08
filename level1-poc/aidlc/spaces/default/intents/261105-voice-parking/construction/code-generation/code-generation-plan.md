# Code Generation Plan: VoiceParking 프로토타입

## Summary

- Builds: 음성 텍스트 → 최적 주차권 추천 → 모의 결제(웹훅) → 예약 완료 표시까지 되는 VoiceParking 프로토타입 (Vue 3 프런트 + Spring Boot 마이크로서비스 3개)
- Touches: 새 폴더 `backend/`(recommendation·payment·reservation 서비스), `frontend/`, `README.md`는 건드리지 않고 `RUN.md`를 새로 만듦
- Tests: 서비스별 JUnit 단위 테스트와 프런트 vitest, 요구사항당 1개 이상(약 20개)

## Design Decisions (이 계획이 정하는 것)

요구사항(`requirements.md`)이 설계로 미룬 항목을 이 계획에서 아래처럼 정합니다. 승인 시 함께 확정됩니다.

- 서비스 3개로 나눕니다: `recommendation-service`(8081), `payment-service`(8082), `reservation-service`(8083). 프런트는 Vite 개발 서버의 프록시로 `/api/recommend`, `/api/payments`, `/api/reservations`를 각 서비스로 보냅니다. (제약: Vue 3, Spring Boot, 모듈이 여럿이면 마이크로서비스)
- 저장소는 서비스마다 메모리(`ConcurrentHashMap`)입니다. 프로토타입이므로 DB는 쓰지 않습니다.
- 흐름: 추천 → 주차권 선택 → `reservation-service`가 `PENDING` 예약을 만들고 승인 마감(180초)을 기록 → `payment-service`가 결제를 `PENDING`으로 만듦 → 모의 PG 시뮬레이터가 비동기 웹훅(`POST /api/payments/webhook`)을 호출 → `payment-service`가 `reservation-service`에 확정을 요청 → `reservation-service`가 SSE로 화면에 `CONFIRMED`를 알림.
- 예약 상태: `PENDING`(승인 대기), `CONFIRMED`(확정), `CANCELLED`(취소). 180초가 지나도 승인이 없으면 예약 서비스의 스케줄러가 `CANCELLED`로 바꿉니다. 정확히 180초 경계는 취소로 봅니다.
- 늦은 승인: 이미 `CANCELLED`인 예약에 승인이 오면 확정하지 않고(HTTP 409), 결제는 `LATE_APPROVAL_IGNORED`로 기록합니다. 같은 결제의 중복 웹훅은 한 번만 처리합니다(멱등). 모르는 결제 ID의 웹훅은 거절합니다(변경 요청 이후 응답은 아래 "결제 오류 응답 규칙"을 따릅니다).
- 결제 오류 응답 규칙(사용자 변경 요청): 결제 서비스의 결제 실패·오류 응답은 모두 HTTP 402 Payment Required와 표준 에러 객체 `{ "code": "...", "message": "..." }`로 통일합니다. 입력 검증 실패, 모르는 결제 ID, 예약 서비스 연결 실패, 결제 실패 사유 등 결제 관련 예외가 모두 해당하며, 앞으로 추가하는 결제 관련 예외도 같은 규칙을 따릅니다.
- 점수 환산: 후보 3개 이상일 때 가격·거리는 후보 중 최솟값 대비 비율로 0~100점(낮을수록 고득점), 이용 시간 적합도는 요청 시간과 같으면 100점, 한 단위(1시간)가 어긋날 때마다 50점씩 감점(최소 0). 종합 = 가격×0.4 + 거리×0.4 + 적합도×0.2. 동점이면 가격이 낮은 쪽, 그래도 같으면 ID 순서.
- "근처": 샘플 데이터의 목적지(강남역) 기준 반경 1,000m 이내를 후보 풀로 봅니다. 후보가 3개 미만이면 있는 만큼만 보여 주고 "비교 후보가 부족하다"는 안내를 붙입니다.
- 모의 PG: 화면에 "승인" / "실패" 시연 버튼을 둡니다. 둘 다 `payment-service`의 `/api/mock-pg/{paymentId}/approve|fail`을 호출하고, 이것이 비동기로 웹훅을 보냅니다.
- 입력 해석: 한글 숫자("두 시간")와 "N시간"을 지원합니다. 목적지나 이용 시간을 찾지 못하면 재입력 안내를 돌려줍니다.

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "org",
  "ordering": "implement each applicable testable layer, then write and run that layer's tests.",
  "scope": "poc",
  "test_strategy": "minimal",
  "project_type": "greenfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
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
    "methodology": "test-after",
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
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:c5db1280ba0333b655bcf97bdcaeaa0fd9cc0f50807a6ee879935b8aff3e17e0",
  "contract_sha256": "sha256:be5041ec2297dded1a31f328fae6a86555cf649f231018ea8168c9bb8dd811a5"
}
```

## Steps

테스트 순서는 test-after입니다: 계층을 구현한 뒤 그 계층의 테스트를 씁니다. DB가 없으므로 "데이터 모델/DB" 계층은 메모리 도메인 모델로 갈음합니다.

- [ ] Step 1: 프로젝트 구조와 설정 뼈대 — `backend/pom.xml`(Java 17, Spring Boot 3, 모듈 3개), 서비스별 `pom.xml`·`application.yml`(포트 8081/8082/8083), `frontend/`(Vite + Vue 3 + vitest) 생성 (FR 전체, 제약)
- [ ] Step 2: 테스트 실행 환경 — JUnit 5(`spring-boot-starter-test`)와 vitest 설정, 서비스별로 범위가 좁은 실행 명령을 `unit-test-instructions.md`에 기록
- [ ] Step 3: 도메인 모델 구현 — `ParkingTicket`, `RecommendationRequest/Result`, `Reservation`(+상태), `Payment`(+상태) (FR2, FR3, FR4)
- [ ] Step 4: 도메인 모델 테스트 작성·실행 — 상태 전이 규칙(`PENDING`→`CONFIRMED`/`CANCELLED`, 취소 후 확정 금지) (FR3.4, FR4.1)
- [ ] Step 5: 저장소 구현 — 샘플 주차권 데이터(강남역 반경 내외 5건 이상), 예약·결제 인메모리 저장소 (FR2, FR4)
- [ ] Step 6: 저장소 테스트 작성·실행 — 반경 1,000m 필터, 저장·조회 (FR2.2)
- [ ] Step 7: 비즈니스 로직 구현 — 입력 해석기, 종합 점수 계산기, 추천 설명 생성, 예약 만료 스케줄러, 결제 웹훅 처리(멱등·늦은 승인), 예약 확정 요청 (FR1, FR2.1~2.3, FR3.2~3.4, FR4.1, NFR2)
- [ ] Step 8: 비즈니스 로직 테스트 작성·실행 — FR1.1, FR1.2, FR2.1(40/40/20·동점 규칙), FR2.2, FR2.3, FR3.2, FR3.3, FR3.4/NFR2(만료), FR4.1, 늦은 승인·중복 웹훅
- [ ] Step 9: API 구현 — `POST /api/recommend`, `POST /api/reservations`, `GET /api/reservations/{id}`, `GET /api/reservations/{id}/events`(SSE), `POST /api/payments`, `POST /api/payments/webhook`, `POST /api/mock-pg/{paymentId}/approve|fail`, 서비스 간 `POST /internal/reservations/{id}/confirm` (FR1~FR4)
- [ ] Step 10: API 테스트 작성·실행 — MockMvc로 추천 응답(후보 3개·설명), 웹훅 승인→예약 확정, 취소된 예약 409, 모르는 결제 404, 승인 웹훅 수신→SSE 완료 이벤트까지 1초 이내(NFR1)
- [ ] Step 11: 프런트엔드 구현 — 입력창, 추천 카드 3개(최적 강조·이유 표시), 결제 시작, 모의 PG 시연 버튼, 결제 대기 카운트다운(180초), 결제 실패 안내와 재결제, 예약 완료 화면(SSE, 실패 시 폴링) 모든 상호작용 요소에 `data-testid` 부여 (FR1~FR4)
- [ ] Step 12: 프런트엔드 테스트 작성·실행 — 추천 카드 렌더링(3개·최적 표시), 결제 실패 후 재결제 버튼, 예약 완료 화면 표시 (FR2.2, FR2.3, FR3.3, FR4.2)
- [ ] Step 13: 환경·빌드 설정 — Vite 프록시, 서비스 기동 스크립트(`run-all.sh`), `.gitignore`는 건드리지 않고 필요한 무시 항목은 `RUN.md`에 안내
- [ ] Step 14: 문서와 추적성 — `RUN.md`(실행 방법·시연 순서), `code-summary.md`, `traceability.json`
- [x] Step 15: (변경 요청) 결제 오류 응답 통일 — `payment-service`의 모든 결제 실패·오류 응답을 HTTP 402 + `{code, message}`로 통일(공통 예외 처리기), 프런트가 이 형식을 읽어 사유를 보여 주게 수정, 관련 테스트(`PaymentApiTest`, `PaymentServiceTest`, 프런트 `PaymentPanel.spec.js`)를 새 규칙에 맞게 수정·실행, `RUN.md`/`code-summary.md` 갱신. (사용자가 R-01은 브라우저로 직접 확인하기로 함)

## Story-to-Step Traceability

| 요구사항 | 단계 |
|---|---|
| FR1.1, FR1.2 | Step 7, 8, 9, 11 |
| FR2.1 | Step 7, 8 |
| FR2.2 | Step 5, 6, 8, 11, 12 |
| FR2.3 | Step 7, 8, 11, 12 |
| FR3.1 | Step 9, 11 |
| FR3.2 | Step 7, 8, 9, 10 |
| FR3.3 | Step 7, 8, 11, 12 |
| FR3.4, NFR2 | Step 4, 7, 8 |
| FR4.1 | Step 4, 7, 8, 10 |
| FR4.2, NFR1 | Step 9, 10, 11, 12 |

## Out of Scope (이 계획이 만들지 않는 것)

실제 PG 연동, 실제 주차장 데이터, 진짜 음성 인식, 로그인·계정, DB 영속화, 컨테이너·배포 파일. (`requirements.md` Out of Scope + 프로토타입 판단)

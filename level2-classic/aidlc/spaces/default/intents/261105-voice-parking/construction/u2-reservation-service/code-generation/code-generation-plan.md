# u2-reservation-service 코드 생성 계획

## Summary

- Builds: 예약 상태를 확정하고(결제 대기 → 완료/실패/180초 자동 취소) 상태 변화를 SSE로 푸시하는 예약 서비스(Java 17, Spring Boot 3, Maven)
- Touches: 저장소 루트의 `pom.xml`(모듈 추가)과 `services/u2-reservation-service/` (소스, 설정, 시험)
- Tests: 단위·웹 계층 시험 약 35개를 TDD(실패하는 시험 먼저)로 작성하고 JaCoCo 80% 하한을 빌드에 건다

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] functional-spec.md, rules.md(BR1.1~BR5.3), entities.md, nfr-requirements/(tech-stack-decisions 포함), nfr-design/(performance, security, scalability, reliability, observability, logical-components), contract-summary.md(C3, C4, C5), unit-of-work.md(U2), team.md(TDD, 80% 하한, Java IDE 기본, Docker 금지), project.md(로컬 실행 스크립트)

## 설계 검토에서 넘어온 결정

설계 단계의 검토가 남긴 항목은 설계 파일이 이미 검토를 마쳐 고칠 수 없으므로 이 계획이 구현 결정으로 닫는다. 아래 결정은 설계 문서와 어긋나는 곳에서 이 계획이 우선한다.

| # | 항목 | 결정 |
|---|---|---|
| D1 | 멱등 재요청의 200 응답 `status` (BR1.2, 계약 C5의 enum은 PENDING_PAYMENT뿐) | 기존 예약의 실제 현재 상태를 돌려준다. 계약 C5 200 응답의 `status` 값 범위가 넓어지는 것이므로 u3에 넘기는 사항으로 `code-summary.md`에 적는다. 종료된 예약을 받은 u3가 모의 PG에 다시 결제를 요청할지는 u3 설계에서 정한다 |
| D2 | 같은 키 동시 요청 (BR1.2, BR1.3 순서) | 세션 잠금 안에서 멱등 키 조회를 먼저, 결제 대기 확인을 그다음에 한다. 뒤의 요청은 201이 아닌 200으로 같은 예약을 받는다. u1 호출은 잠금 밖에서 하므로 한 번 낭비될 수 있다 |
| D3 | 결제 결과 요청 검증 (BR2.1, 계약 C5에 400 없음) | eventId 1~100자, outcome이 APPROVED·FAILED가 아니면 400 INVALID_REQUEST. 계약에 응답 하나를 더하는 것이며 u3에 넘긴다 |
| D4 | 마감 후 발견한 취소의 `cancelledAt` (BR2.3, BR2.4) | 결과 반영 경로가 발견해 취소하면 `cancelledAt`은 마감 시각(paymentRequestedAt + 180초)으로 둔다. 타이머가 취소하면 취소를 확정한 시각이다. 마감 판정은 이 서비스가 요청을 받은 시각 기준이다 |
| D5 | 푸시 실행기 (nfr-design 2차 검토) | 스레드 상한은 구독 수 상한의 2배(64)다. 실행기가 작업을 거절하면 그 연결을 닫는다(화면이 재연결하며 최신 스냅샷을 받는다). 쓰기가 막힌 스레드의 반환에 의존하지 않는다 |
| D6 | u1 시도 제한 | 350ms로 구현한다(performance-design.md). logical-components.md의 500ms 표기는 오기다 |
| D7 | 구독 상한 503 | 구독 수 상한(32) 초과 시 503 `SUBSCRIPTION_LIMIT`. 연결을 닫을 때 구독을 먼저 제거한 뒤 상한을 센다. 화면은 일반 오류로 취급해 잠시 뒤 재시도한다. 계약 C3에 응답 하나를 더하는 것이며 u4에 넘긴다 |
| D8 | 중복 eventId 로그 | 중복 재응답도 `cause=DUPLICATE`로 로그 한 줄을 남긴다(NFR5.3) |

## 코드 배치

저장소 루트(작업 공간)에 둔다. 이 유닛의 `source-manifest.json`에 만든 파일을 모두 적는다.

```
pom.xml                                   상위 pom (modules에 services/u2-reservation-service 추가)
services/u2-reservation-service/pom.xml
services/u2-reservation-service/src/main/java/kr/voiceparking/reservation/
    ReservationApplication.java
    domain/ (Reservation, ReservationStatus, AppliedEvent, Outcome, ApplyResult)
    reservation/ (ReservationService, ReservationStore, DeadlineTimers, SearchClient, PassInfo, CreateCommand, ReservationException, ErrorCode)
    notification/ (Snapshot, NotificationService, Connection, SnapshotSink)
    web/ (ReservationController, EventsController, InternalReservationController, ApiExceptionHandler, RequestLogFilter, SessionIds, dto 레코드)
    config/ (TimeConfig, PushConfig, SearchClientConfig, ReservationProperties)
services/u2-reservation-service/src/main/resources/application.properties
services/u2-reservation-service/src/test/java/kr/voiceparking/reservation/ (시험)
```

패키지 의존 방향: `reservation` → `notification`(스냅샷을 밀어 넣음), `notification`은 `reservation`과 `domain`을 참조하지 않는다. `web`이 둘을 잇는다.

## Steps

- [x] Step 1: 프로젝트 골격과 운영 설정. 상위 `pom.xml`에 모듈을 추가하고 서비스 `pom.xml`(Spring Boot 3, Java 17, web, validation, actuator, 시험 의존성, JaCoCo 라인 커버리지 80% 하한 검사)을 만든다. `application.properties`: 포트 `${SERVER_PORT:8082}`, `server.address=${SERVER_ADDRESS:127.0.0.1}`, `search.service.url=${SEARCH_SERVICE_URL:http://127.0.0.1:8081}`, `reservation.payment-timeout=180s`, actuator는 health만 노출. (NFR4.3, NFR6.5, NFR7.1, NFR8.1, NFR1.5)
- [x] Step 2: 시험 실행 준비. 서비스 단위 명령 `mvn -q -pl services/u2-reservation-service test`가 실행되는지 확인하고 `unit-test-instructions.md`에 기록한다. (NFR7.1)
- [x] Step 3: 데이터 모델 - Red. 도메인 시험을 먼저 쓰고 실패 출력을 기록한다: 새 예약은 PENDING_PAYMENT·version 1, 허용된 세 전이만 가능하고 종료 상태는 바뀌지 않음, 전이마다 version +1과 상태별 message와 시각 필드(confirmedAt·failedAt·cancelledAt 중 하나만), Snapshot 변환. (BR1.5, BR2.5, 엔티티 제약)
- [x] Step 4: 데이터 모델 - Green. `Reservation`(불변, 전이 메서드), `ReservationStatus`, `AppliedEvent`, `Outcome`, `ApplyResult`, `Snapshot`을 구현한다.
- [x] Step 5: 데이터 모델 - Refactor. 시험이 통과한 채로 정리한다.
- [x] Step 6: 저장소 - Red. `ReservationStore` 시험: reservationId 조회, (sessionId, idempotencyKey) 조회, 세션의 결제 대기 예약 조회, 세션의 가장 최근 예약, 판정 기록 저장·조회, 예약 단위·세션 단위 잠금이 서로 다른 키끼리 막지 않음. (BR1.2, BR1.3, BR3.3, BR5.2, NFR4.2)
- [x] Step 7: 저장소 - Green. 인메모리 맵과 `ConcurrentHashMap` 기반 잠금 객체 관리를 구현한다. (BR5.3)
- [x] Step 8: 저장소 - Refactor.
- [x] Step 9: 비즈니스 로직 - Red. 시험을 먼저 쓴다. 시계는 주입한 시험용 시계, u1은 가짜 `SearchClient`, Notification은 기록용 가짜 `SnapshotSink`를 쓴다.
  - 예약 생성: 정상 생성(금액·주차장 이름은 u1 결과), 형식 오류 400, 멱등 재요청은 같은 예약(타이머·푸시 추가 없음), 결제 대기 중 다른 키는 409와 기존 ID, 같은 키 동시 2건은 예약 1건과 201·200, 결제 실패 뒤 새 예약 허용, u1의 404·422 전달과 실패 시 502.
  - 결제 결과: APPROVED → CONFIRMED(푸시 1회), FAILED → PAYMENT_FAILED, 같은 eventId 3회는 확정 1회와 같은 판정, 다른 eventId의 승인 두 번째는 REJECTED, 마감 전 179초 승인은 CONFIRMED, 180초·181초 승인은 CANCELLED 후 REJECTED(`cancelledAt`은 마감 시각), 취소된 예약의 늦은 승인은 되살아나지 않음, 예약 없음 404.
  - 자동 취소: 마감 시각 타이머 콜백이 결제 대기를 CANCELLED로(안내 문구, 푸시), 이미 종료된 예약에는 아무것도 안 함, 결과 반영으로 벗어나면 타이머 해제, 승인과 취소 경합에서 정확히 하나만 이김.
  - u1 호출: 350ms 시도·200ms 간격·1.5초 마감으로 최대 3회, 마감 안에 실패하면 502.
  - 조회: 세션 격리(다른 세션은 404), 현재 예약, 예약 없음.
  (BR1.1~BR1.5, BR2.1~BR2.7, BR3.1~BR3.3, BR5.1, BR5.2, NFR2.1, NFR2.3, NFR5.1, NFR5.2, NFR5.5)
- [x] Step 10: 비즈니스 로직 - Green. `ReservationService`, `DeadlineTimers`, `SearchClient`(RestClient + 직접 반복)를 구현한다. 시각은 모두 `Clock`에서, 타이머는 같은 시계를 쓰는 스케줄러에서 읽는다. (D1~D4, D6, D8)
- [x] Step 11: 비즈니스 로직 - Refactor.
- [x] Step 12: 알림 - Red. `NotificationService` 시험을 먼저 쓴다: version이 높은 스냅샷만 보관·전송, 구독 등록과 최신 스냅샷이 첫 항목, 같은 연결에서 version 단조 증가(연달아 발행), 구독 등록과 발행이 동시에 일어나도 첫 이벤트가 최신이고 이후가 빠짐없음, 한 연결의 쓰기 실패가 다른 연결에 영향 없음, 큐 16 초과 시 그 연결만 끊김, 쓰기 1초 초과 연결 끊김, 연결 유지 신호가 같은 연결 큐로 나감, 구독 상한 초과 시 거절, 실행기 거절 시 연결 닫힘. (BR4.1~BR4.5, NFR1.8, NFR4.4, NFR5.4, D5, D7)
- [x] Step 13: 알림 - Green. `NotificationService`, `Connection`(연결별 FIFO 큐와 배출 작업), 푸시 실행기와 감시 스케줄러 설정을 구현한다. `notification` 패키지가 `reservation`·`domain`을 참조하지 않도록 한다.
- [x] Step 14: 알림 - Refactor.
- [x] Step 15: API 계층 - Red. MockMvc 시험을 먼저 쓴다: C5 예약 생성 201·200·400·404·409·422·502, 결제 결과 반영 200(APPLIED·REJECTED)·400·404, C3 현재 예약 200·404 NO_RESERVATION·401, 단건 조회 200·404(다른 세션 포함), 구독 연결 시 첫 이벤트 snapshot(id = version)·401·404·503, 헤더가 쿼리보다 우선, 처리 못한 예외 500 INTERNAL_ERROR, 오류 본문에 입력 원문 없음. (BR1.1, BR2.1, BR3.1~BR3.3, BR4.3, NFR5.6, NFR6.2, NFR6.3, NFR6.6)
- [x] Step 16: API 계층 - Green. `ReservationController`(C3 조회), `EventsController`(SSE, 소유 확인과 대체 스냅샷), `InternalReservationController`(C5), `ApiExceptionHandler`(공통 오류 형식), `RequestLogFilter`(경로·상태·시간·세션 앞 8자), 상태 전이·거부·푸시 로그를 구현한다. `SseEmitter` 시간 초과는 10분. (NFR1.6~NFR1.8, NFR5.9, NFR5.10)
- [x] Step 17: API 계층 - Refactor.
- [x] Step 18: 성능·보안·구조 확인 시험. 결과 반영→연결 쓰기 150ms(구독 연결 있음)와 100ms(없음), 구독 첫 이벤트 200ms, 예약 생성 500ms를 예열 20회 제외 100회 최댓값으로 확인, 느린 클라이언트 1~4개가 있어도 다른 예약 푸시 150ms 이내, 동시 10 세션, 마감을 2초로 줄인 실제 스케줄러 경로 1건, 실제 180초 취소 시험(`slow` 표시, 일상 실행에서 제외), 소스·설정에 비밀 값 패턴 없음, 기본 `server.address`가 루프백이고 비루프백 주소 접속이 거부됨(주소가 없으면 건너뜀을 기록), `notification` 패키지가 `reservation`을 참조하지 않음. (NFR1.1~NFR1.4, NFR2.1, NFR2.2, NFR4.1, NFR4.5, NFR6.1, NFR6.5)
- [x] Step 19: 프론트엔드 동작. 이 유닛은 서비스라서 해당 없음.
- [x] Step 20: 환경·빌드 설정. 커버리지 하한을 `mvn verify`에서 검사하도록 확인한다(`slow` 시험은 병합 전 검증 실행에 포함하는 프로필을 둔다). 하한은 낮추지 않는다. (NFR7.1, NFR8.2)
- [x] Step 21: 문서와 추적성. `code-summary.md`(만든 파일, 실행 방법, 기술 선택, 다른 유닛에 넘기는 사항 D1·D3·D7·루프백 주소·100자 상한), `traceability.json`, `source-manifest.json`을 쓴다.

## 스토리 추적

| 스토리·인수 조건 | 계획 단계 |
|---|---|
| US3.1 AC3.1.1~AC3.1.3 | Step 3, 4, 6, 7, 9, 10, 12, 15, 16 |
| US3.2 AC3.2.1~AC3.2.3 | Step 3, 4, 9, 10, 12, 15, 16 |
| US4.1 AC4.1.1 | Step 9, 10, 12, 13, 15, 16, 18 |
| US4.1 AC4.1.2 | Step 6, 9, 10, 15 |
| US4.1 AC4.1.3 | 이 유닛 범위 밖(u3가 서명 검증을 맡음) |
| US4.1 AC4.1.4 | Step 6, 9, 12, 13, 15, 16 |
| US4.1 AC4.1.5 | Step 9, 10, 15 |
| US4.2 AC4.2.1~AC4.2.4 | Step 9, 10, 12, 15, 16, 18 |
| 계약 C3, C5 | Step 15, 16 |

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

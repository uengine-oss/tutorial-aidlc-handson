# u3-payment-service 코드 생성 계획

## Summary

- Builds: 결제 요청을 받아 예약 서비스에 대기 예약을 만들고 모의 PG에 결제를 요청하며, 모의 PG가 보내는 서명된 승인·실패 웹훅을 멱등하게 처리해 예약에 반영하는 결제 서비스(Java 17, Spring Boot 3, Maven)
- Touches: 저장소 루트의 `pom.xml`(모듈 추가)과 `services/u3-payment-service/` (소스, 설정, 시험)
- Tests: 단위·웹 계층 시험 약 45개를 TDD(실패하는 시험 먼저)로 작성하고 JaCoCo 80% 하한을 빌드에 건다

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] functional-spec.md, rules.md(BR1.1~BR5.1), entities.md, nfr-requirements/(tech-stack-decisions 포함), nfr-design/(performance, security, scalability, reliability, observability, logical-components), contract-summary.md(C2, C5, C6), unit-of-work.md(U3), u2-reservation-service의 code-summary.md(구현된 C5), team.md(TDD, 80% 하한, Java IDE 기본, Docker 금지), project.md(로컬 실행 스크립트)

## 설계 검토에서 넘어온 결정

설계 단계의 검토가 남긴 항목은 설계 파일이 이미 검토를 마쳐 고칠 수 없으므로 이 계획이 구현 결정으로 닫는다. 아래 결정은 설계 문서와 어긋나는 곳에서 이 계획이 우선한다.

| # | 항목 | 결정 |
|---|---|---|
| E1 | 웹훅의 잠금 순서 (nfr-design 2차 검토, 흐름도가 낡음) | 이벤트 잠금 → 중복 조회 → paymentId 기준 Payment 잠금 → u2 결과 반영 호출 → 이벤트·상태 기록이다. Payment 잠금은 u2 호출 앞에서 얻고 쥔 채 호출한다 |
| E2 | u2 호출의 총 소요 시간 | 예약 생성(마감 1.5초, 요청 수신 시각 기준)과 결과 반영의 시도(2초)는 연결·읽기 제한만으로 두지 않고 호출 전체에 총 소요 시간 제한을 건다(별도 실행기에서 호출하고 남은 시간 안에 못 받으면 취소하고 실패 처리). 응답을 조금씩 흘리는 가짜 u2 시험을 추가한다 |
| E3 | 웹훅의 잠금 대기 상한 | 이벤트 잠금과 Payment 잠금의 대기는 각각 7초까지다. 넘으면 502 UPSTREAM_FAILED로 끝내고 이벤트를 기록하지 않는다. 같은 Payment에 웹훅이 몰리는 것은 위조·시험 상황에 한정된다 |
| E4 | 모의 PG 시간 초과 로그 | 자기 호출의 시간 초과는 `PG_CALLBACK_NO_RESPONSE`, 연결 거부·오류 응답은 `PG_CALLBACK_FAILED`, 큐 거부는 `PG_CALLBACK_DROPPED`로 남긴다 |
| E5 | 의존 방향 시험 범위 | `payment`, `web`(웹훅 포함), `signature` 패키지의 소스 import에 `pg`가 없어야 한다. `pg`를 import해도 되는 곳은 `config`뿐이다. `pg`는 `web`을 참조하지 않는다 |
| E6 | 성능 시험 재실행 규칙 | 첫 실행이 한도를 넘었을 때 한 번만 다시 실행하며, 첫 실행의 초과가 단발 이상값(중앙값의 5배 이상)일 때만 허용한다. 그렇지 않으면 실패다 |
| E7 | 다른 유닛에 넘기는 사항 | (a) u4·u5: Vite 프록시는 `POST /api/payments`만 정확 일치로 연결하고 `/api/payments/webhook`은 연결하지 않는다. (b) u4: 같은 키 재요청에서 예약이 종료되었으면 200과 현재 status가 오고(202·PENDING_PAYMENT 고정이 아님), 결제 요청의 세션 오류는 400 MISSING_SESSION이다. (c) u5: `PAYMENT_WEBHOOK_SECRET`을 이 서비스 한 곳에만 주입하고 루프백 바인딩 기본값을 바꾸지 않는다. 모두 `code-summary.md`에 적는다 |
| E8 | 기능 설계 검토에서 넘어온 항목 | 같은 (세션, 멱등 키)는 키 잠금 안에서 u2 호출·Payment 조회 또는 생성·모의 PG 등록(PgRequest가 없을 때만)을 한다. 이미 종료된 Payment에 새 eventId가 오면 u2에는 전달하되 Payment.status는 바꾸지 않고 WebhookEvent만 기록한다. 같은 키에 다른 passId·durationHours가 와도 기존 결과를 돌려준다. Payment는 있으나 PgRequest가 없고 예약이 결제 대기이면 그때 모의 PG에 등록한다. 웹훅 본문은 8KB까지 한정 읽기로 읽고 크기 검사(400) → 서명(401) → 형식(400) → 알려진 결제(404) 순서다 |

## 코드 배치

저장소 루트(작업 공간)에 둔다. 이 유닛의 `source-manifest.json`에 만든 파일을 모두 적는다.

```
pom.xml                                   상위 pom (modules에 services/u3-payment-service 추가)
services/u3-payment-service/pom.xml
services/u3-payment-service/src/main/java/kr/voiceparking/payment/
    PaymentApplication.java
    payment/ (Payment, PaymentStatus, WebhookEvent, PaymentStore, PaymentService, PaymentGateway, PgPayment, ReservationClient, HttpReservationClient, ReservationReply, PaymentException, ErrorCode)
    signature/ (HmacSigner)
    pg/ (MockPaymentGateway, PgOutcome, WebhookCaller)
    web/ (PaymentController, WebhookController, WebhookBodyFilter, SignatureVerifier, ApiExceptionHandler, RequestLogFilter, SessionIds, dto 레코드)
    config/ (TimeConfig, PgConfig, ReservationClientConfig, PaymentProperties)
services/u3-payment-service/src/main/resources/application.properties
services/u3-payment-service/src/test/java/kr/voiceparking/payment/ (시험)
```

패키지 의존 방향: `pg` → `payment`·`signature`, `web` → `payment`·`signature`, `payment`는 `pg`·`web`을 참조하지 않는다.

## Steps

- [x] Step 1: 프로젝트 골격과 운영 설정. 상위 `pom.xml`에 모듈을 추가하고 서비스 `pom.xml`(Spring Boot 3, Java 17, web, validation, actuator, 시험 의존성, JaCoCo 라인 커버리지 80% 하한 검사)을 만든다. `application.properties`: 포트 `${SERVER_PORT:8083}`, `server.address=${SERVER_ADDRESS:127.0.0.1}`, `reservation.service.url=${RESERVATION_SERVICE_URL:http://127.0.0.1:8082}`, `payment.webhook.url=${WEBHOOK_URL:http://127.0.0.1:8083/api/payments/webhook}`, `payment.webhook.secret=${PAYMENT_WEBHOOK_SECRET}`(기본값 없음), actuator는 health만 노출. (NFR4.3, NFR6.2, NFR6.7, NFR7.1, NFR8.1, NFR1.7)
- [x] Step 2: 시험 실행 준비. 서비스 단위 명령 `mvn -q -pl services/u3-payment-service test`가 실행되는지 확인하고 `unit-test-instructions.md`에 기록한다. (NFR7.1)
- [x] Step 3: 데이터 모델 - Red. 도메인 시험을 먼저 쓰고 실패 출력을 기록한다: 새 Payment는 REQUESTED, 허용된 전이(REQUESTED → APPROVED·FAILED·IGNORED)만 가능하고 종료 상태는 바뀌지 않음, WebhookEvent의 handled 값. (BR2.1, BR3.5, 엔티티 제약)
- [x] Step 4: 데이터 모델 - Green. `Payment`(불변, 전이 메서드), `PaymentStatus`, `WebhookEvent`, `PgPayment`를 구현한다.
- [x] Step 5: 데이터 모델 - Refactor.
- [x] Step 6: 저장소 - Red. `PaymentStore` 시험: paymentId 조회, (sessionId, idempotencyKey) 조회, 이벤트 기록·조회, 서로 다른 키·이벤트·Payment의 잠금이 서로 막지 않음, 같은 키의 잠금은 순서대로, 대기 상한(`tryLock`) 초과 시 실패. (BR1.3, BR3.4, NFR4.2)
- [x] Step 7: 저장소 - Green. 인메모리 맵과 `ConcurrentHashMap` 기반 잠금 객체 관리를 구현한다. (BR5.1)
- [x] Step 8: 저장소 - Refactor.
- [x] Step 9: 비즈니스 로직 - Red. 시험을 먼저 쓴다. 시계는 주입한 시험용 시계, u2는 가짜 `ReservationClient`, 모의 PG는 가짜 `PaymentGateway`를 쓴다.
  - 서명: `HmacSigner`가 알려진 벡터와 일치, 형식 검증(`sha256=<64자 소문자 hex>`), 상수 시간 비교.
  - 결제 요청: 새 결제(u2 호출 1회, Payment REQUESTED, 모의 PG 등록 1회, 금액·requestedAt은 u2 값), 형식 오류 400, u2의 404·409(reservationId 포함)·422 전달, 그 밖의 u2 오류·시간 초과는 502 UPSTREAM_FAILED와 Payment 없음, 같은 키 재요청(u2 200)은 새 Payment·새 모의 PG 등록 없음, 종료된 예약을 돌려받으면 모의 PG 재등록 없이 현재 status, Payment는 있으나 PgRequest가 없을 때 결제 대기 예약이면 등록, 같은 키 동시 3건은 Payment 1건·모의 PG 1회, 첫 u2 호출이 응답 없을 때 둘째 요청도 요청 수신부터 1.7초 안에 502, 결제 실패 뒤 새 키 요청 허용.
  - 웹훅: 새 이벤트(u2 호출 1회, APPLIED → Payment APPROVED 또는 FAILED, 이벤트 기록), 같은 eventId 3회(순차)·동시 3건은 u2 호출 1회와 DUPLICATE, u2 REJECTED는 IGNORED 기록과 Payment IGNORED, 종료된 Payment에 새 eventId는 Payment 불변, u2 실패(재시도 후)는 502와 이벤트 미기록과 같은 이벤트 재전송 시 처음부터 처리, 같은 Payment에 서로 다른 eventId 동시 2건은 Payment가 u2 판정을 따름, 알 수 없는 paymentId 404, 잠금 대기 7초 초과 502.
  - u2 호출: 예약 생성은 재시도 없음·마감 1.5초(응답을 흘려도), 결과 반영은 시도당 2초·간격 200ms·최대 3회.
  - 모의 PG: APPROVE는 바로 APPROVED, DELAY는 지정 초(0이면 200초) 뒤 APPROVED(주입 시계), FAIL은 바로 FAILED, 알림은 서명된 JSON(eventId 새 UUID), 한 Payment에 한 번, 자기 호출 실패는 재전송 없이 `PG_CALLBACK_FAILED`·`PG_CALLBACK_NO_RESPONSE` 로그, 큐 거부는 `PG_CALLBACK_DROPPED`.
  (BR1.1~BR1.5, BR2.1, BR3.1~BR3.6, NFR5.1~NFR5.7)
- [x] Step 10: 비즈니스 로직 - Green. `HmacSigner`, `PaymentService`, `HttpReservationClient`, `MockPaymentGateway`(시계·스케줄러 주입, 스케줄러와 발송 실행기 분리)를 구현한다. 시각은 모두 `Clock`에서 읽는다. (E1~E4, E8)
- [x] Step 11: 비즈니스 로직 - Refactor.
- [x] Step 12: API 계층 - Red. MockMvc 시험을 먼저 쓴다: C2 결제 요청 202·200(종료된 예약 재요청)·400 MISSING_SESSION·400 INVALID_REQUEST·404·409·422·502, C6 웹훅 200(APPLIED·DUPLICATE·IGNORED)·401 INVALID_SIGNATURE(서명 없음·형식 오류·불일치·본문 변조)·400(형식 오류·8KB 초과, 크기가 서명보다 먼저)·404 UNKNOWN_PAYMENT·502, 처리 못한 예외 500 INTERNAL_ERROR, 오류 본문에 입력 원문 없음, 기동 시 비밀 값이 없으면 실패. (BR1.1, BR3.1~BR3.3, NFR5.8, NFR6.1~NFR6.5)
- [x] Step 13: API 계층 - Green. `PaymentController`(C2), `WebhookController`(C6), `WebhookBodyFilter`(웹훅 경로 전용 8193바이트 한정 읽기, 요청 로그 필터보다 안쪽), `SignatureVerifier`, `ApiExceptionHandler`(공통 오류 형식), `RequestLogFilter`(경로·상태·시간·세션 앞 8자), 웹훅 결과·결제 요청·원인 코드 로그를 구현한다. (NFR1.8, NFR1.9, NFR5.12~NFR5.14)
- [x] Step 14: API 계층 - Refactor.
- [x] Step 15: 성능·보안·구조 확인 시험. 웹훅 처리 150ms(정상 가짜 u2), 중복 20ms, 서명 거부 20ms, 결제 요청 500ms를 예열 20회 제외 100회 최댓값으로 확인(E6의 재실행 규칙), 응답 없는 u2로 결제 요청 502까지 1.7초 이하, 지연 알림 50건 제때 발송(±1초), 동시 10 세션, 서명 거부·중복·무시 로그에 본문·서명 값이 없음, 소스·설정(`src/main`)에 비밀 값 패턴 없음, 기본 `server.address`가 루프백, 의존 방향(E5). (NFR1.1~NFR1.6, NFR4.1, NFR4.4, NFR4.5, NFR6.1~NFR6.3, NFR6.7)
- [x] Step 16: 프론트엔드 동작. 이 유닛은 서비스라서 해당 없음.
- [x] Step 17: 환경·빌드 설정. 커버리지 하한을 `mvn verify`에서 검사하도록 확인한다. 하한은 낮추지 않는다. (NFR7.1, NFR8.2)
- [x] Step 18: 문서와 추적성. `code-summary.md`(만든 파일, 실행 방법, 환경 변수, 기술 선택, 다른 유닛에 넘기는 사항 E7), `traceability.json`, `source-manifest.json`을 쓴다.

## 스토리 추적

| 스토리·인수 조건 | 계획 단계 |
|---|---|
| US3.1 AC3.1.1 | Step 9, 10, 12, 13 |
| US3.1 AC3.1.2 | 이 유닛 범위 밖(u4가 표시하고 u2가 상태를 확정). 202 PENDING_PAYMENT 접수만 Step 12, 13 |
| US3.1 AC3.1.3 | Step 9, 10, 12, 13 (u2의 409 전달) |
| US3.2 AC3.2.1 | Step 9, 10, 12, 13 |
| US3.2 AC3.2.2 | Step 9, 10, 12 |
| US3.2 AC3.2.3 | 이 유닛 범위 밖(u2가 종료 상태 불변을 보장) |
| US4.1 AC4.1.1 | Step 9, 10, 12, 13, 15 |
| US4.1 AC4.1.2 | Step 6, 9, 10, 12 |
| US4.1 AC4.1.3 | Step 9, 10, 12, 13, 15 |
| US4.1 AC4.1.4 | 이 유닛 범위 밖(u2) |
| US4.1 AC4.1.5 | Step 9, 10, 12 |
| 계약 C2, C6 | Step 12, 13 |

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

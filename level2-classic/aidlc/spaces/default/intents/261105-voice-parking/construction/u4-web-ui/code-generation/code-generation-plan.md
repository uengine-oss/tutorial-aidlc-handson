# u4-web-ui 코드 생성 계획

## Summary

- Builds: 입력, 추천 결과, 결제 승인 대기, 예약 완료·실패·자동 취소(S1~S8)를 보이고 SSE로 실시간 갱신하는 Vue 3 단일 페이지와 서비스 3개를 잇는 Vite 개발 서버 프록시
- Touches: `services/u4-web-ui/` 아래의 npm 프로젝트 전체(소스, 프록시 설정, 린트 설정, 시험). 저장소 루트의 기존 파일은 바꾸지 않는다
- Tests: Vitest로 단위·컴포넌트·시간·프록시 통합 시험 약 90개를 TDD(실패하는 시험 먼저)로 작성하고 라인 커버리지 80% 하한을 건다

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] functional-spec.md(W1~W5, UR1.1~UR8.1), rules.md(BR1.1~BR8.1), frontend-components.md, nfr-requirements/(performance, security, tech-stack-decisions), nfr-design/(performance, security, logical-components), contract-summary.md(C1, C2, C3), refined-mockups/(mockups.md, interaction-spec.md, accessibility-checklist.md, design-system-mapping.md), unit-of-work.md(U4), u1·u2·u3의 code-summary.md(구현된 계약), team.md(Vue 3, ESLint + Prettier, TDD, 80% 하한, Docker 금지), project.md(로컬 실행 스크립트)

## 설계 검토에서 넘어온 결정

설계 단계의 검토가 남긴 항목은 설계 파일이 이미 검토를 마쳐 고칠 수 없으므로 이 계획이 구현 결정으로 닫는다. 아래 결정은 설계 문서와 어긋나는 곳에서 이 계획이 우선한다.

| # | 항목 | 결정 |
|---|---|---|
| F1 | 시간 예산 배분 (기능 설계 검토) | 화면 반영 100ms + 프록시 전달 100ms(합 200ms, 계약 C3 배분 그대로). 판정은 p99가 한도 이하이고 최댓값이 한도의 2배 이하다(틱은 p99 5ms, 최댓값 20ms). 기능 설계의 "화면 200ms"는 이 결정으로 대체한다 |
| F2 | 프록시 시험 (기능 설계 검토) | 실제 Vite `createServer`가 `vite.proxy.js`를 읽어 기동한다. 개발 서버와 시험용 서비스 서버는 임의 포트(0)로 띄워 개발 서버 5173과 충돌하지 않게 한다. 독립 http-proxy를 쓰지 않는다 |
| F3 | 시험 프로젝트와 직렬 실행 (NFR 설계 검토) | Vitest 프로젝트 `unit`(jsdom, 병렬 가능), `timing`(jsdom, `*.timing.test.js`, 순차), `proxy`(node, `*.proxy.test.js`, 순차, 시간 판정 시험 포함). `timing`과 `proxy`는 서로 동시에 돌지 않는다(`test`는 프로젝트를 `unit` → `timing` → `proxy` 순서로 실행하는 스크립트). `test:coverage`는 `unit`만 계측한다 |
| F4 | 커버리지 범위 | `coverage.include`는 `src/**`와 `vite.proxy.js`다. 제외는 `src/main.js`, `src/**/*.test.js`뿐이며 `vite.config.js`는 설정 파일이라 제외 목록에 명시한다. `unit` 시험만으로 `vite.proxy.js`를 포함한 전체 라인 80%를 넘도록 프록시 설정의 순수 함수(`bypass`, `assertLoopbackHost`, 설정 값)를 `unit`에서 직접 시험한다 |
| F5 | 호스트 검사 값 목록 (NFR 설계 검토) | `assertLoopbackHost`의 시험 값: `127.0.0.1`, `localhost`, `LOCALHOST`, `::1`, `[::1]`는 허용, `0.0.0.0`, `true`(`--host`만 준 경우), `undefined`/빈 값, 외부 IP, `192.168.0.5`는 거부(`UI_ALLOW_NON_LOOPBACK=1`이면 경고와 함께 허용). 실제 Vite를 CLI `--host`로 시작하면 거부됨을 통합 시험에서 확인한다 |
| F6 | 금지 API 목록 | Vue 3 형태로 정한다: 템플릿 `v-html`, DOM의 `innerHTML`·`outerHTML`·`insertAdjacentHTML`, `document.write`, `h()`·`v-bind`로 `innerHTML`·`domProps` 키를 넘기는 것, `eval`·`new Function`. ESLint(`vue/no-v-html`, `no-restricted-properties`, `no-restricted-syntax`, `no-eval`, `no-new-func`)와 소스 검색 시험이 함께 막는다 |
| F7 | 비밀 값 패턴 검색 | 대상: `src/**`(시험 제외), `vite.config.js`, `vite.proxy.js`, `index.html`, `.env*`. 패턴: `(?i)(api[_-]?key\|secret\|token\|password)\s*[:=]\s*['"][^'"]{8,}['"]`, `-----BEGIN [A-Z ]*PRIVATE KEY-----`, `AKIA[0-9A-Z]{16}`. 시험 소스는 제외한다. 패턴이 실제로 잡는지 시험용 문자열로 자체 확인한다 |
| F8 | 결제 시도 보관 (기능 설계 검토) | 보관 시도는 `{ passId, durationHours, key }`다. passId 또는 durationHours가 다르면 새 키를 쓴다. 400과 401 응답은 확정된 거절로 보아 보관 시도를 지우고 접수 오류 안내를 보인다. 응답을 받지 못한 경우(네트워크, 3초, 502, 5xx)만 보관을 유지한다 |
| F9 | 상태 전이 표 보완 (기능 설계 검토) | S7에서 추천 목록이 없을 때 "새로 검색하기"로 S1에 가는 전이, 결제 409(기존 대기 예약으로 pending), 단건·현재 조회 404(예약 영역 비우고 S1)를 구현하고 시험한다 |
| F10 | 복구 안내 문구 (기능 설계 검토) | "실시간 연결이 불안정합니다. 새로 고침해 주세요", "세션을 확인하지 못했습니다. 새로 고침해 주세요", "예약 상태를 불러오지 못했습니다. 새로 고침해 주세요" 세 문구를 `ErrorNotice`로 보이고 aria-live에 알린다 |
| F11 | 지연 초 범위 | 시연 설정의 지연 초는 1~600(기본 200)으로 입력 검증, 컴포넌트 속성, 문서 모두 통일한다 |
| F12 | 실패 카드 표시 | `lastPaidPassId`는 202 또는 200일 때만 기록하고 새로 시작에서 지운다. 실패 표시는 현재 예약이 failed이고 목록에 그 passId가 있을 때만 한다 |
| F13 | 통합 검증 명령 (NFR7.2) | 이 유닛은 `npm run verify`(lint → test → test:coverage → build → audit:report)를 정의한다. 프로젝트의 Construction Verification Command(`mvn -q verify`)에 화면 시험을 묶는 일은 Unit 체크포인트에서 사람이 승인하는 명령으로 정한다. 이 계획은 루트 파일을 바꾸지 않는다 |
| F14 | 다른 유닛에 넘기는 사항 | 시연 절차에 "결제 대기 중 탭은 하나"(브라우저 오리진당 6연결 한도), u5는 `npm run dev`로 기동하고 `U1_URL`·`U2_URL`·`U3_URL`을 필요하면 준다. `code-summary.md`에 적는다 |

## 코드 배치

`services/u4-web-ui/`에 둔다. 이 유닛의 `source-manifest.json`에 만든 파일을 모두 적는다.

```
services/u4-web-ui/package.json, package-lock.json
services/u4-web-ui/index.html
services/u4-web-ui/vite.config.js         assertLoopbackHost 플러그인(configResolved), 프록시 설정 연결, strictPort
services/u4-web-ui/vite.proxy.js          프록시 설정 값, bypass, assertLoopbackHost (순수 함수)
services/u4-web-ui/vitest.config.js       프로젝트 unit / timing / proxy, 커버리지 설정
services/u4-web-ui/eslint.config.js, .prettierrc, .prettierignore
services/u4-web-ui/scripts/run-tests.mjs  unit → timing → proxy 순차 실행
services/u4-web-ui/src/main.js, src/App.vue
services/u4-web-ui/src/components/        QueryInput, ErrorNotice, ParkingCard, RecommendationList, ReservationStatus, EmptyNotice, ScenarioPanel (.vue)
services/u4-web-ui/src/composables/       useApp, useSession, useReservationStream (.js)
services/u4-web-ui/src/api/api.js
services/u4-web-ui/src/lib/               guards.js(응답 형식 확인), format.js(요금·시간 표시), messages.js(안내 문구)
services/u4-web-ui/src/styles/main.css
services/u4-web-ui/src/**/*.test.js, test/*.timing.test.js, test/*.proxy.test.js, test/support/
```

## Steps

- [x] Step 1: 프로젝트 골격. `package.json`(스크립트 `dev`, `test`, `test:unit`, `test:coverage`, `lint`, `build`, `audit:report`, `verify`), Vite·Vue 3·Vitest·`@vue/test-utils`·jsdom·`@vitest/coverage-v8`·ESLint(flat)·`eslint-plugin-vue`·Prettier의 서로 호환되는 최신 안정 버전을 `npm install`로 설치하고 `package-lock.json`을 고정한다. `index.html`, `vite.config.js`, `vitest.config.js`(프로젝트 3개), `eslint.config.js`, `.prettierrc`를 만든다. 루트 파일은 바꾸지 않는다. (NFR4.1, NFR7.1, NFR9.1, NFR8.2)
- [x] Step 2: 시험 실행 준비. `npm --prefix services/u4-web-ui run test:unit`이 실행되는지 확인하고 `unit-test-instructions.md`의 명령으로 기록한다. (NFR7.1)
- [x] Step 3: 순수 모델 - Red. 시험을 먼저 쓰고 실패 출력을 기록한다: `guards.js`(추천·예약 스냅샷·오류 본문 형식 확인, 알 수 없는 status·정수 아닌 version·필드 누락 거부), `format.js`(천 단위 구분 요금, "N시간 N원"), 상태 값 매핑(status → 화면 상태). (BR2.3, BR4.2, NFR6.5)
- [x] Step 4: 순수 모델 - Green. `guards.js`, `format.js`, `messages.js`를 구현한다.
- [x] Step 5: 순수 모델 - Refactor.
- [x] Step 6: 데이터 접근 - Red. 시험을 먼저 쓴다: `useSession`(sessionStorage에서 UUID 읽기·만들기, 저장소 불가 시 메모리 유지, `crypto.randomUUID` 없을 때 대체, 추천 목록 저장·복원·삭제), `api`(상대 경로, `X-Session-Id` 헤더, 3초 제한 시간, 오류 본문 파싱, 구독 URL의 `sessionId` 쿼리가 구독 URL 함수에만 있음, 세션 ID 로그 비기록). (BR1.1, BR1.2, BR1.4, BR2.6, NFR6.4, NFR3.2)
- [x] Step 7: 데이터 접근 - Green. `useSession.js`, `api.js`를 구현한다.
- [x] Step 8: 데이터 접근 - Refactor.
- [x] Step 9: 비즈니스 로직 - Red. `useApp`과 `useReservationStream` 시험을 먼저 쓴다. 가짜 `fetch`, 가짜 `EventSource`, 주입 시계(`now()`)를 쓴다.
  - 추천: 전송 시 로딩과 중복 전송 방지, 200 후보 있음(S3)·없음(S4), 422 MISSING_PLACE·MISSING_DURATION(S2, 이전 목록 비움), 네트워크·5xx·3초 초과 안내와 이전 결과 유지, 공백 입력 비활성.
  - 결제: 202(S5와 구독), 200+PENDING_PAYMENT(S5와 구독), 200+종료 상태(단건 조회로 S6~S8 채움, 조회 실패 안내), 409(기존 예약으로 S5), 404·422 사용 불가 안내, 502·시간 초과 접수 오류 안내, 결제 중 모든 결제 버튼 비활성, 결제 대기 중 새 검색 허용과 버튼 비활성.
  - 멱등 키(F8): 같은 후보 재시도는 같은 키, 다른 후보·다른 이용 시간·확정 응답(202·200·409·404·422·400·401) 뒤는 새 키, 새로 시작에서 삭제.
  - 구독: 예약별 version 필터(새 예약의 version 1 반영), 구독 교체 때 기존 구독 닫기, 종료 상태에서 닫기, `pagehide`에서 닫기, 깨진 JSON·다른 reservationId·정수 아닌 version 무시.
  - 복구: CLOSED 뒤 1·2·5초 물러나기와 총 60초 한도, 단건 조회 404는 S1과 중단, 401은 세션 안내와 중단, 종료 상태면 중단, 60초 뒤 새로 고침 안내, S1로 돌아가면 중단.
  - 경과 시간: 0~180 보정(시계 어긋남), 180 도달 시 "승인 여부를 확인하는 중입니다...", 틱이 결제 대기가 끝나면 해제.
  - 처음 열기: `/current` 404면 S1, 예약별 상태 복원(S5는 구독 시작), 저장된 추천 복원, 실패 카드 표시(F12), S7에서 목록이 없으면 "새로 검색하기".
  - 새로 시작: 두 영역·구독·복구·저장 목록·보관 시도·`lastPaidPassId`를 비움.
  (BR1.3, BR2.2~BR2.6, BR3.1~BR3.7, BR4.1~BR4.5, BR5.1, NFR5.1, F8~F10, F12)
- [x] Step 10: 비즈니스 로직 - Green. `useApp.js`, `useReservationStream.js`를 구현한다. 현재 시각은 주입 가능한 `now()`에서 읽는다.
- [x] Step 11: 비즈니스 로직 - Refactor.
- [x] Step 12: 프록시와 서버 설정 - Red. `unit` 시험을 먼저 쓴다: `vite.proxy.js`의 설정 값(`/api/search`→U1, `/api/reservations`→U2 SSE 포함 타임아웃 600000ms·`Accept-Encoding: identity`, 결제 키 `^/api/payments$`), `bypass`(POST만 통과, GET·쿼리 문자열은 `false`), 대상 주소 환경 변수, `assertLoopbackHost`(F5 값 목록). `proxy` 통합 시험도 먼저 쓴다(실제 Vite `createServer`, 임의 포트, 시험용 u1·u2·u3 서버): 웹훅 경로·끝 슬래시·`GET /api/payments`·쿼리가 붙은 경로는 시험용 u3 수신 0, `/internal/**`는 시험용 u1·u2·u3 모두 수신 0, `POST /api/payments`·`/api/search/...`·`/api/reservations/...`는 해당 서비스에 닿음, CLI `--host` 비루프백은 시작 거부, 포트 사용 중이면 실패. (BR8.1, NFR6.3, NFR6.6, NFR8.1)
- [x] Step 13: 프록시와 서버 설정 - Green. `vite.proxy.js`, `vite.config.js`(`configResolved`에서 `config.server.host` 검사, `strictPort: true`)를 구현한다.
- [x] Step 14: 프록시와 서버 설정 - Refactor.
- [x] Step 15: 프런트엔드 동작 - Red. 컴포넌트 시험을 먼저 쓴다(`@vue/test-utils`): QueryInput(레이블 연결, 엔터와 버튼 동작, 공백 비활성, 200자, 로딩 문구), ParkingCard(순위 문구, 요금 표시, 이유 문구, 비활성·실패 표시, `data-testid`), RecommendationList(서버 순서 유지, 3개 미만), ReservationStatus(상태별 표시, 경과 시간, 확인 중 문구, 재시작 단추, 목록 없을 때 실패 상태의 새로 검색하기), ErrorNotice, EmptyNotice, ScenarioPanel(지연 초 1~600, 지연일 때만 입력), App(두 영역 조합, aria-live 사건 목록, 경과 시간은 aria-live 밖, 사용자 동작에서만 상태 제목으로 포커스 이동·복원 시 입력창 포커스, 서버 문자열 `<script>`가 글자로만 보임). (BR2.3, BR2.4, BR3.4, BR3.7, BR4.6, BR6.1, BR7.1, NFR6.1)
- [x] Step 16: 프런트엔드 동작 - Green. 컴포넌트, `App.vue`, `main.js`, `main.css`(디자인 토큰 CSS 변수, 포커스 윤곽선, 대비 4.5:1 이상, 오류·실패·취소의 `(!)` 문자)를 구현한다. 서버 문자열은 `{{ }}`로만 그린다.
- [x] Step 17: 프런트엔드 동작 - Refactor.
- [x] Step 18: 성능·보안·구조 확인 시험. `*.timing.test.js`: 이벤트 수신→DOM 반영 100ms(p99, 최댓값 200ms 이하), 추천 응답→카드 100ms, 경과 시간 틱 p99 5ms·최댓값 20ms, 복구 간격과 총 시간(가짜 시계). `*.proxy.test.js`: 프록시 SSE 전달 지연 차이 p99 100ms·최댓값 200ms(이벤트 120개 50ms 간격, 앞 20개 예열, 묶임 판정은 인접 간격이 25ms 미만, 응답에 `Content-Encoding` 없음), 프록시 시간 초과 설정 값. 소스 검색 시험: 금지 API(F6)와 비밀 값 패턴(F7)이 없음과 패턴의 자체 확인. 판정은 예열 20회 버리고 100회, 출력에 중앙값·p99·최댓값을 남기며 재실행으로 가리지 않는다. (NFR1.1~NFR1.4, NFR3.1, NFR6.1, NFR6.2, NFR6.3)
- [x] Step 19: 린트·포맷·빌드 설정. ESLint 규칙(F6)과 Prettier를 `npm run lint`가 검사하게 하고 `vite build`가 통과하는지, `verify`가 순서대로 실행되는지 확인한다. 커버리지 하한 80%는 낮추지 않는다. `package-lock.json`을 커밋 대상에 포함한다. (NFR7.1, NFR9.1, NFR6.7)
- [x] Step 20: 문서와 추적성. `code-summary.md`(만든 파일, 실행 방법, 환경 변수, 기술 선택, F13·F14와 다른 유닛에 넘기는 사항, `audit:report` 출력의 높음·치명 취약점 기록), `traceability.json`, `source-manifest.json`을 쓴다.

## 스토리 추적

| 스토리·인수 조건 | 계획 단계 |
|---|---|
| US1.1 AC1.1.1~AC1.1.3 | Step 9, 10, 15, 16 |
| US1.1 AC1.1.4 | Step 6, 7, 9, 10 |
| US2.1 AC2.1.1, AC2.1.3 | Step 9, 10, 15, 16 |
| US2.1 AC2.1.2 | 이 유닛 범위 밖(u1이 제외) |
| US2.2 AC2.2.2, AC2.2.3, AC2.2.5, AC2.2.6 | Step 9, 10, 15, 16, 18 |
| US2.2 AC2.2.1, AC2.2.4 | 이 유닛 범위 밖(u1이 계산·정렬) |
| US3.1 AC3.1.1~AC3.1.3 | Step 9, 10, 15, 16 |
| US3.2 AC3.2.1, AC3.2.2 | Step 9, 10, 15, 16 |
| US3.2 AC3.2.3 | 이 유닛 범위 밖(u2) |
| US4.1 AC4.1.1, AC4.1.4 | Step 9, 10, 15, 16, 18 |
| US4.1 AC4.1.2, AC4.1.3, AC4.1.5 | 이 유닛 범위 밖(u2, u3) |
| US4.2 AC4.2.1 | Step 9, 10, 15, 16 |
| US4.2 AC4.2.2~AC4.2.4 | 이 유닛 범위 밖(u2) |
| 계약 C1, C2, C3 소비 | Step 6, 7, 9, 10, 12, 13 |

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

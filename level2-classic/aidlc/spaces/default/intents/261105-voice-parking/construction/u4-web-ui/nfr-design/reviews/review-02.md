## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-07T15:52:00Z
**Iteration:** 2
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/performance-design.md > 만든 스크립트 정의 (npm), verify 행 및 audit:report 행; traceability.json > NFR6.7 | NFR6.7 요구사항은 "병합 전 검증에서 `npm audit`의 알려진 취약점 보고(높음·치명)를 출력한다"이다. 그런데 `verify`는 "`lint` → `test:coverage` → `build` 순서"이고 `audit:report`는 별도 스크립트라 어느 병합 전 명령도 audit을 실행하지 않는다. 게다가 `audit:report`는 종료 코드 0이며 "레지스트리에 닿지 못하면 'audit 불가'를 출력한다"고만 해서 실행 여부를 아무도 확인하지 못한다. traceability.json은 NFR6.7을 "OK"로 표시한다. | `verify`의 마지막 단계(또는 별도 병합 전 명령)에 `audit:report`를 넣어 출력이 남게 하되 종료 코드는 0으로 유지한다. 또는 audit을 병합 전 검증에서 빼고 NFR6.7을 수동 확인 항목으로 바꾼다. 어느 쪽이든 traceability 행을 실제 결정에 맞춘다. | Resolved (reviewer) |
| R-02 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/security-design.md > 루프백 바인딩 행; performance-design.md > `dev` 스크립트 행 | 호스트 검사는 환경 변수 `UI_HOST`만 읽는다("값이 루프백(...)이 아니면 시작할 때 오류로 거부"). Vite는 CLI 플래그(`npm run dev -- --host 0.0.0.0`)가 설정 파일의 `server.host`를 덮어쓰므로, 설정 파일 안의 환경 변수 검사로는 이 경로를 막지 못한다. NFR6.6의 "루프백에서만 연다"가 우회된다. 포트도 `strictPort`가 없으면 5173이 사용 중일 때 조용히 5174로 올라가 `dev`의 "포트 5173"과 어긋나고, 계약의 5173과 L3 절차가 깨진다. 검사 코드가 어느 파일에 있는지(`vite.proxy.js`인지 `vite.config.js`인지)와 어떻게 시험하는지도 정해져 있지 않다. | 검사를 Vite가 해석을 마친 뒤의 값(`configResolved` 훅의 `config.server.host`)에 건다. 검사 로직은 순수 함수로 `vite.proxy.js`에 두고 호스트 값 목록(`::1`의 대괄호 표기, 대소문자 포함)을 시험한다. `server.strictPort: true`를 명시한다. 시험에 "CLI `--host`로 비루프백을 주면 거부"를 추가한다. | Resolved (reviewer) |
| R-03 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/performance-design.md > 판정 통계, 만든 스크립트 정의 (npm) | NFR1.3(틱 p99 ≤ 5ms, 최댓값 ≤ 20ms)과 NFR1.1(p99 ≤ 100ms) 같은 시간 시험이 `test` 한 번에 L2(실제 Vite 서버 기동)와 같은 실행에 섞이고, `test:coverage`는 v8 계측으로 더 느려진 상태에서 `verify`가 같은 시험을 다시 돌린다. 지터 대책은 통계 규칙뿐이다("재실행으로 가리지 않는다"). 병렬 파일 실행 여부와 시험 환경 분리(L1은 jsdom, L2는 Node)가 정해져 있지 않다. u3에서 겪은 호스트 부하 문제를 그대로 되풀이할 수 있고, 하한은 약화할 수 없어(Testing Posture) 실패 시 빠져나갈 길이 없다. | 시간 민감 시험을 별도 Vitest 프로젝트로 분리해 순차 실행(`fileParallelism: false` 또는 단일 워커)하게 정의한다. L1과 L2의 환경 지정 방식(프로젝트별 `environment` 또는 파일 단위 지시)을 적는다. 측정을 계측 없는 `test`에서만 판정할지, `test:coverage`에서도 같은 한도를 적용할지 정한다. | Unresolved |
| R-04 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/performance-design.md > 판정 통계 (L2 판정 기준) | 설계는 L2를 "이벤트 100개(50ms 간격)의 p99 ≤ 100ms, 최댓값 ≤ 200ms이며 이벤트 개수가 같고 한꺼번에 도착한 이벤트가 없어야 한다"로 정한다. 한도(100ms)가 이벤트 간격(50ms)보다 커서, 이벤트 두 개가 묶여 도착해도 지연 차이만으로는 합격한다. 그런데 "한꺼번에 도착"의 수치 정의가 없다. 업스트림 NFR1.2의 "20개, 250ms 간격"도 이 설계가 100개·50ms로 바꾸면서 변경 사실이 적혀 있지 않다. | "한꺼번에 도착"을 수치로 정의한다(예: 인접 이벤트의 수신 시각 간격이 보낸 간격의 절반 미만이면 묶임으로 판정). 업스트림 값을 바꾼 이유(표본 수를 늘려 p99 의미를 확보)를 한 줄로 남긴다. | Resolved (reviewer) |
| R-05 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/performance-design.md > 프록시는 통과만 행 | "개발 서버 압축 미들웨어가 이벤트 스트림을 모으지 않도록 `text/event-stream` 응답은 압축 대상에서 제외한다"는 구현 수단이 없는 서술이다. Vite 개발 서버는 기본 압축 미들웨어가 없고, 실제 위험은 업스트림이 `Accept-Encoding`을 받아 압축하는 경우다. 또 "`timeout`/`proxyTimeout`은 끄거나 10분 이상"은 두 가지 값이 열려 있고, L2 시험은 6초 남짓(120이벤트 × 50ms)이라 이 설정을 검증하지 못한다. | 압축 항목을 구체화한다(프록시 요청의 `Accept-Encoding` 제거 또는 `identity` 고정, 응답 `Content-Encoding` 없음을 L2에서 확인). 타임아웃은 한 값으로 정하고(예: 예약 자동 취소 180초보다 긴 값) 설정이 그 값임을 확인하는 단위 시험을 추가한다. | Resolved (reviewer) |
| R-06 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/security-design.md > 결제 프록시 정확 일치 행 | "`bypass(req)` 함수가 ... 프록시를 건너뛰게 한다"고만 쓰고 반환값을 정하지 않는다. Vite의 `bypass`는 `false`를 돌려주면 404, 문자열이면 그 경로로 대체, `null`/`undefined`/`true`이면 그대로 프록시한다. 구현자가 `true`나 빈 반환으로 "건너뜀"을 표현하면 오히려 u3로 전달된다. 업스트림 NFR6.3은 "404"를 확인한다고 적었는데 설계는 "시험용 u3가 받은 요청 수 0"으로 바꿨다(타당하다). 그러나 같은 시험이 시험용 u1/u2에도 `/internal/**` 요청이 닿지 않음을 확인한다는 문장은 없다. | `bypass`가 막아야 할 때 `false`를 돌려준다고 명시한다. 합격 기준에 "시험용 u1·u2·u3 모두 `/internal/**`와 웹훅 경로에서 수신 0"을 포함한다. | Resolved (reviewer) |
| R-07 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/performance-design.md > 복구 행, logical-components.md > useReservationStream 행 | "EventSource 구독 하나"만 적혀 있고 브라우저 연결 한도는 다루지 않는다. 개발 서버는 HTTP/1.1이라 오리진당 동시 연결 6개 한도가 있어, 예약 대기 탭이 여러 개이면 SSE가 이를 차지하고 다른 탭의 일반 fetch가 대기하다 3초 제한 시간에 걸린다. 서버 쪽 `503 SUBSCRIPTION_LIMIT`(기능 명세 UR4.4에 있음)과의 관계도 적혀 있지 않다. 새로 고침이나 탭 닫기 때 구독을 닫는다는 설명도 없다. | 한계를 명시하고 받아들이거나(프로토타입에서는 탭 1~2개 가정) 시연 절차에 "대기 중 탭은 하나"를 적는다. 구독 교체 시 기존 구독을 먼저 닫는 규칙과 `pagehide`에서 닫는 규칙을 시험 항목에 넣는다. | Resolved (reviewer) |
| R-08 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/security-design.md > 경계 검증 행 | 응답 형식 확인 함수(`isReservation` 등)는 "필드 존재와 형식"과 알 수 없는 `status`만 다룬다. SSE 데이터의 JSON 해석 실패(잘못된 JSON이 예외로 처리기를 멈추게 하지 않는 것), 구독 중인 예약과 다른 `reservationId`의 스냅샷, 정수가 아닌 `version`은 시험 목록(`## 시험`)에 없다. | 시험 목록에 세 경우를 추가하고, 잘못된 JSON은 try/catch로 버리고 경고만 남긴다고 적는다. | Resolved (reviewer) |
| R-09 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/performance-design.md > 판정 통계 (구간 합 700ms 줄) | "화면 200ms(한도 100ms × 2) + 프록시 200ms + u3 웹훅 처리 150ms + u2 반영과 푸시 150ms = 700ms"는 앞의 두 항목에만 2배 최댓값 규칙을 적용하고 뒤의 두 항목은 p99 수준 한도 그대로다. 같은 2배 규칙을 적용하면 200+200+300+300 = 1000ms로 남는 여유가 없다. "구간 최댓값 상한의 합"이라는 문장이 사실과 다르다. 틱 시험도 "2배"가 아닌 4배(20ms)를 쓴다. | 합산 줄을 "화면·프록시는 최댓값 상한, u3·u2는 계약의 한도"로 표현을 정정하거나, 다른 유닛의 최댓값 규칙이 확정될 때까지 합산 검증은 Build and Test의 몫이라고 적는다. | Resolved (reviewer) |
| R-10 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/performance-design.md > 만든 스크립트 정의 (npm), 커버리지 범위 | `coverage.include`는 `src/**`와 `vite.proxy.js`이고 제외는 `src/main.js`와 시험 파일뿐이다(방향은 좋다). 그러나 L2가 "`createServer`가 `vite.proxy.js`를 읽어 기동"한다고만 적어서, 시험이 설정 파일 경로로 기동하면 Vite의 설정 로더가 모듈을 임시 번들로 평가해 v8 커버리지가 `vite.proxy.js`에 귀속되지 않을 수 있다. `vite.config.js`는 포함도 제외도 아니어서 상태가 불명확하다. | 시험이 `vite.proxy.js`를 직접 import해 `createServer({ server: { proxy } })`에 인라인으로 넘긴다고 적는다. `vite.config.js`는 배선만 담는다고 명시하고 제외 목록에 이유와 함께 넣는다. | New |
| R-11 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/security-design.md > 텍스트로만 렌더링 행, 비밀 값 없음 행 | 금지 API 목록에 "렌더 함수의 `domProps: { innerHTML }`"가 있는데 이는 Vue 2 문법이고 Vue 3에서는 `h('div', { innerHTML })`이다. 린트 규칙이 이 형태(객체 속성 키 `innerHTML`)를 잡는지 적혀 있지 않다. 비밀 값 패턴 검색은 "패턴"의 내용이 없어 시험할 수 없다. | 금지 API 항목을 Vue 3 형태로 고치고 `no-restricted-syntax` 선택자가 속성 키도 다룬다고 쓴다. 비밀 값 패턴을 최소 목록(예: `secret`, `token`, `apikey`, `password`, 긴 hex·base64 리터럴)으로 적는다. | New |
| R-12 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/performance-design.md > 판정 통계 > 마지막 항목(시간 민감 시험은 별도 Vitest 프로젝트로 분리), 만든 스크립트 정의 > `test` | `fileParallelism: false`와 단일 워커로 파일 간 순차 실행을 말하지만, `test`는 `vitest run` 한 번으로 `unit`, `timing`, `proxy`를 함께 돌린다. 프로젝트 사이의 동시 실행을 막는 장치가 적혀 있지 않아 L2(`proxy` 프로젝트)의 p99 ≤ 100ms 판정과 `timing`의 틱 p99 ≤ 5ms 판정이 다른 프로젝트의 부하와 겹칠 수 있다. 또한 L2의 시간 판정 시험이 `*.timing.test.js` 이름 규칙으로 `timing` 프로젝트에 속하는지 `proxy` 프로젝트에 속하는지 정해져 있지 않다. | `timing`과 `proxy`의 시간 판정 시험이 어떤 프로젝트에서 어떤 순서로 실행되는지 정한다. 예를 들어 `test`를 프로젝트별 순차 호출(`unit` → `timing` → `proxy`)로 쪼개거나 프로젝트 간 직렬화 설정을 쓴다고 적고, L2 시간 시험의 소속 프로젝트를 명시한다. | New |
| R-13 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/security-design.md > 루프백 바인딩 (`assertLoopbackHost(host, env)`) | 값 목록 시험은 `127.0.0.1`, `localhost`, `::1`, `[::1]`뿐이다. Vite가 해석한 `config.server.host`는 CLI의 `--host`(값 없음)에서는 `true`, 설정을 안 했을 때는 `undefined`, `0.0.0.0`일 수 있다. 이 값들을 허용할지 거부할지 적혀 있지 않아 가장 흔한 비루프백 경로(`vite --host`)가 시험 목록에서 빠질 위험이 있다. | `true`와 `0.0.0.0`은 거부, `undefined`는 Vite 기본(localhost)이므로 허용 또는 기본값으로 대체한다고 정하고 `assertLoopbackHost` 값 목록 시험에 넣는다. | New |
| R-14 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/performance-design.md > 만든 스크립트 정의 마지막 문단(커버리지 범위), security-design.md > 루프백 바인딩 | 호스트 검사 플러그인은 `vite.config.js`에, 로직은 `vite.proxy.js`에 두는데 `coverage.include`는 `src/**`와 `vite.proxy.js`뿐이고 `vite.config.js`는 포함도 제외도 아니다. 또한 `test:coverage`는 `unit` 프로젝트만 계측하는데 `bypass`·호스트 검사의 실제 통합 시험은 `proxy` 프로젝트에 있어, 80% 하한에 `vite.proxy.js`가 기여하려면 `unit`에 `bypass`·`assertLoopbackHost`·내보낸 설정값 단위 시험이 있어야 한다. 이 의존이 적혀 있지 않다. | `vite.config.js`의 처리(포함 또는 사유를 적은 제외)를 정하고, `vite.proxy.js`의 라인이 `unit` 시험만으로 80%를 넘는다고 확인하는 규칙(또는 `proxy` 프로젝트도 계측 대상에 넣는다는 결정)을 적는다. | New |
| R-15 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/security-design.md > 텍스트로만 렌더링 (금지 API 목록) | 금지 목록에 Vue 2 문법인 렌더 함수 `domProps: { innerHTML }`이 들어 있다. 이 유닛은 Vue 3라 `h('div', { innerHTML: x })`처럼 속성으로 바로 넘기며 `domProps` 키가 없다. ESLint `no-restricted-syntax`와 소스 검색이 `domProps` 문자열만 겨냥하면 실제 Vue 3 우회 경로(`h`의 `innerHTML` 속성, `v-bind="{ innerHTML }"`)를 놓친다. | 목록을 Vue 3 형태로 고쳐 `innerHTML` 속성 키 자체(객체 리터럴의 키, `:innerHTML` 바인딩, `v-bind` 객체)를 `no-restricted-syntax`와 소스 검색의 대상으로 적는다. | New |
| R-16 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/security-design.md > 비밀 값 없음 | "비밀 값 패턴 검색 시험(`src/**`, 설정 파일; 시험 소스는 제외)"이라고만 하고 어떤 패턴(예: 긴 토큰 문자열, `secret`·`token`·`apikey` 이름에 대입된 리터럴)과 어떤 설정 파일이 대상인지 정의하지 않았다. 시험이 아무것도 못 잡아도 통과해 요구사항 NFR6.2의 근거가 약하다. | 검색할 패턴 목록, 대상 파일 목록(`vite.config.js`, `vite.proxy.js`, `package.json`, `.env*` 등), 예외(예: 기본 URL의 `127.0.0.1`)를 적고, 패턴이 실제로 잡는지 보이는 양성 표본 시험을 함께 둔다. | New |
| R-17 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/nfr-design/performance-design.md > 시험 계층 L2, security-design.md > 웹훅 차단의 합격 기준 | L2 시험이 `createServer`로 실제 Vite를 띄우지만, `dev` 스크립트와 `server.strictPort: true`가 포트 5173을 고정하므로 개발 서버가 떠 있는 동안 `npm test`를 돌리면 포트 충돌로 실패하거나, 시험이 포트를 다르게 넘기면 `vite.proxy.js`와 설정 파일의 포트 고정이 시험에서 검증되지 않는다. 시험 서버가 쓰는 포트와 바인딩 방식이 적혀 있지 않다. | L2가 쓰는 포트(예: 포트 0 또는 임시 포트)와 호스트를 명시하고, 포트 점유 실패 시험(5173)만 별도로 임시 점유 포트를 써서 개발 서버와 충돌하지 않게 한다고 적는다. | New |

> R-01 Reviewer note: `audit:report` 스크립트가 정의되었고 `verify`의 마지막 단계(`lint → test → test:coverage → build → audit:report`)로 묶였다. 보고 전용(종료 코드 0)이며 "audit 불가" 출력과 `code-summary.md`·Unit 체크포인트 기록 규칙이 있다.

> R-02 Reviewer note: 호스트 검사가 `vite.config.js` 플러그인의 `configResolved`에서 `config.server.host`를 검사하도록 바뀌어 CLI `--host`도 잡힌다. `server.strictPort: true`와 포트 5173 점유 시 실패 시험이 추가되었다. 다만 `true`·`undefined` 값 처리는 새 항목으로 올린다.

> R-03 Reviewer note: Vitest 프로젝트(`unit`/`timing`/`proxy`) 분리, `*.timing.test.js`, 계측 없는 `test`에서만 시간 판정, `test:coverage`는 `unit`만 계측으로 핵심은 해결되어 Major에서 Minor로 낮춘다. 남은 문제는 `test`가 한 번의 `vitest run`으로 세 프로젝트를 함께 돌려 `timing`과 `proxy`(L2의 p99 100ms 판정 포함)가 서로, 또는 `unit`과 동시에 실행될 수 있다는 점이다. 새 항목 참조.

> R-04 Reviewer note: "한꺼번에 도착"이 인접 수신 간격 25ms 미만으로 정의되었고, 요구사항의 20개·250ms를 120개·50ms로 바꾼 사실과 이유가 명시되었다.

> R-05 Reviewer note: `Accept-Encoding: identity` 고정과 L2의 `Content-Encoding` 부재 확인, `timeout`=`proxyTimeout`=600000ms 단일 값과 이를 확인하는 단위 시험이 명시되었다.

> R-06 Reviewer note: `bypass`가 `false`(404, 프록시 안 함)와 `undefined`(프록시)를 돌려주는 규칙이 명시되었고, `/internal/**`가 u1·u2·u3 모두 받은 요청 수 0임을 확인하는 기준이 추가되었다.

> R-07 Reviewer note: 브라우저의 동시 연결 6개 한계와 시연 절차("결제 대기 중 탭은 하나")가 기록되었고, 구독 교체 시 선(先) 닫기와 `pagehide` 닫기가 설계·시험에 들어갔다.

> R-08 Reviewer note: 깨진 JSON, 다른 `reservationId`, 정수가 아닌 `version`, 알 수 없는 `status`, 필드 누락 스냅샷의 무시 시험이 보안 설계 시험 절에 열거되었다.

> R-09 Reviewer note: 합계가 `200 + 200 + 150 + 150 = 700ms ≤ 1000ms`로 풀어 쓰였고 합산 검증의 책임(Build and Test 실측)이 분명하다.

> R-10 Not re-checked this round

> R-11 Not re-checked this round

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| (이 단계에 지정된 검증 도구 없음) | 해당 없음 | 산출물 4종과 상류 파일을 직접 대조해 검토했다. R-01~R-09의 해결 여부는 위 인용 근거로 확인했다. |

### Summary

첫 검토의 Major 3건(audit 연결, 호스트 검사 우회, 시간 시험 분리)은 설계에서 구체적인 장치(`verify` 단계, `configResolved`+`strictPort`, Vitest 프로젝트 분리)로 닫혔고, 남은 것은 프로젝트 간 동시 실행, `--host` 값 처리, 커버리지 범위, Vue 3 금지 API 표기, 비밀 값 검색 정의, L2 포트 같은 구현 중 막히지 않을 Minor뿐이라 READY로 본다.

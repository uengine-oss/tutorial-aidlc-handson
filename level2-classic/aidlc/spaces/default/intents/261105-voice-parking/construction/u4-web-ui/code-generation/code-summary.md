# u4-web-ui 코드 요약

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] code-generation-plan.md(Step 1~20, 결정 F1~F14), unit-test-instructions.md, functional-design/, nfr-requirements/, nfr-design/, contract-summary.md(C1, C2, C3), u1·u2·u3의 code-summary.md

## 만든 것

입력, 추천 결과, 결제 승인 대기, 예약 완료·실패·자동 취소(S1~S8)를 보이고 SSE로 실시간 갱신하는 Vue 3 단일 페이지(JavaScript, `<script setup>`)와, 서비스 3개를 `/api/...` 상대 경로로 잇는 Vite 개발 서버 프록시다. `services/u4-web-ui/`의 독립 npm 프로젝트이며 개발 서버로만 실행한다(배포하지 않는다).

| 위치 | 내용 |
|---|---|
| `package.json`, `package-lock.json` | 스크립트 `dev`, `test`, `test:unit`, `test:coverage`, `lint`, `build`, `audit:report`, `verify`. 잠금 파일 고정 |
| `vite.config.js`, `vite.proxy.js` | 프록시 값(`/api/search`→u1, `/api/reservations`→u2(SSE, 타임아웃 600000ms, `Accept-Encoding: identity`), `POST /api/payments` 정확 일치→u3), `bypass`, `assertLoopbackHost`(`configResolved`에서 해석된 host 검사), `strictPort: true` |
| `vitest.config.js`, `scripts/run-tests.mjs` | 프로젝트 `unit`·`timing`·`proxy`, 순차 실행, 커버리지(unit만 계측, 라인 80% 하한) |
| `eslint.config.js`, `.prettierrc`, `.prettierignore` | ESLint(flat, `eslint-plugin-vue`)와 Prettier. 금지 API 규칙 |
| `src/composables/` | `useApp`(두 영역 상태, 결제 시도 키, 복원, 경과 시간), `useReservationStream`(구독, version 필터, 복구), `useSession` |
| `src/api/api.js`, `src/lib/` | 요청 래퍼(헤더, 3초 제한), 응답 형식 확인(`guards`), 표시 형식, 안내 문구 |
| `src/components/`, `src/App.vue`, `src/styles/main.css` | 컴포넌트 7개와 앱(텍스트로만 렌더링, aria-live, 포커스 규칙, 디자인 토큰 CSS) |
| `src/**/*.test.js`, `test/` | 단위·컴포넌트·시간·프록시 통합·소스 검색 시험과 지원 도구 |
| `scripts/audit-report.mjs` | `npm audit --audit-level=high` 보고(종료 코드 0, 레지스트리에 닿지 못하면 "audit 불가" 출력) |
| `.gitignore` (프로젝트 로컬) | `node_modules/`, `dist/`, `coverage/`. 루트 `.gitignore`는 건드리지 않았다 |

## 실행 방법

저장소 루트에서:

```
npm --prefix services/u4-web-ui install        # 처음 한 번
npm --prefix services/u4-web-ui run dev        # 개발 서버 (http://127.0.0.1:5173, 포트가 쓰이는 중이면 실패)
npm --prefix services/u4-web-ui run test:unit  # 단위·컴포넌트 시험
npm --prefix services/u4-web-ui run test       # unit → timing → proxy 순차
npm --prefix services/u4-web-ui run test:coverage
npm --prefix services/u4-web-ui run verify     # lint → test → test:coverage → build → audit:report
```

환경 변수: `U1_URL`, `U2_URL`, `U3_URL`(기본 http://127.0.0.1:8081, 8082, 8083), `UI_HOST`(기본 127.0.0.1), `UI_ALLOW_NON_LOOPBACK=1`(비루프백 허용, 경고).

## 시험 결과

- `test:unit`: 17개 파일, 351개 통과. `test` 마지막 실행: unit 351, timing 4, proxy 19, 합계 374 통과.
- 커버리지(`test:coverage`): 라인 98.29%(520/529), 구문 98.39%, 분기 93.9%, 함수 96.71%. 하한 80% 통과.
- `lint`, `build` 통과.
- 시간 시험 수치(마지막 timing 실행, 중앙값/p99/최댓값): 이벤트→DOM 0.09/0.14/0.28ms(한도 100ms), 추천 응답→카드 0.46/7.41/16.06ms(100ms), 틱 0.05/0.12/2.26ms(p99 5ms, 최댓값 20ms), 프록시 SSE 지연 차이 0.14/9.46/10.19ms(p99 100ms).
- **미해결: 틱 시간 시험이 이 머신에서 가끔 실패한다.** 경과 시간 틱(p99 ≤ 5ms, 최댓값 ≤ 20ms)이 호스트 부하(부하 평균 약 200~260)에서 `verify` 4회 중 2회, timing 단독 6회 중 2회, 10회 중 1회 실패했고 한 번은 최댓값 20.28ms로 간신히 넘었다. 중앙값은 0.05ms이고 일부 표본만 10~44ms로 튄다. u3와 같은 현상이다. 목표 수치, 판정 통계, 커버리지 하한은 낮추지 않았고 재실행으로 가리지도 않았다. 부하가 낮을 때 `npm --prefix services/u4-web-ui run verify`를 다시 돌려 통과하는지 확인해야 한다.
- 100ms 한도 시험 두 개는 가끔 p99가 20~44ms까지 튀었지만 한도 안이었고, 프록시 SSE 시험은 매번 통과했다.
- `audit:report`: 높음·치명 취약점 없음. 중간 8건(`glob`, `minimatch` 계열, 개발 의존성). 사람이 판단한다(개발 서버 전용).
- Red 증거: 계층마다 시험을 먼저 썼고 첫 실행은 구현 파일이 없어 `Failed to resolve import` 오류로 스위트가 실패했다(`guards.js`/`format.js`, `api.js`/`useSession.js`, `useApp.js`/`useReservationStream.js`, 컴포넌트와 `App.vue`). 프록시는 unit이 `../vite.proxy.js`를 찾지 못했고 통합 시험은 19개 중 5개 실패, 비루프백 호스트를 거부하지 않았고 SSE 시험은 60초 시간 초과였다. 소스 검색·ESLint 규칙·timing 시험은 이미 있는 코드를 검증하는 시험이라 첫 실행부터 통과했고, 검출력은 금지 패턴이 들어 있는 샘플 문자열로 확인했다.

## 실행하지 않은 시험

- 없음. 작성한 시험을 모두 실행했다. 실제 브라우저에서의 확인(L3 절차: 이벤트가 오면 화면이 1초 안에 바뀌는지, 오프라인 후 복구, 새로 고침 복원)은 계획 범위 밖의 수동 절차이며 Build and Test의 품질 담당이 한다.

## 계획과 달라진 점

- 프록시 단위 시험 파일 이름을 `vite-proxy-config.test.js`로 지었다(`vite.proxy.test.js`는 `*.proxy.test.js` 패턴과 겹쳐 proxy 프로젝트로 잘못 들어간다).
- `audit:report`는 인라인 명령이 아니라 `scripts/audit-report.mjs`다.
- 프로젝트 로컬 `services/u4-web-ui/.gitignore`를 새로 만들었다.
- SSE 묶임 판정은 프록시 경유 수신 간격이 25ms 미만이면서 직접 수신 간격은 25ms 이상이었던 쌍을 묶임으로 본다(호스트 정지로 직접 수신도 붙어 오는 경우를 프록시 탓으로 돌리지 않으려는 것이며 한도 자체는 그대로다).
- `vitest.config.js`에 `silent: false`를 넣어 통과한 시험의 중앙값·p99·최댓값 출력이 보이게 했다.
- 결제 거절 문구를 `MSG_PAYMENT_REJECTED`와 `MSG_REASSIGN_HINT` 둘로 나눠 서버 안내와 겹쳐 출력되지 않게 했다.
- `useApp`은 복구 총 60초를 타이머와 같은 시계로 재기 위해 경과 시간용 `now`와 따로 `deps.recoveryNow`를 받는다. `App.vue`는 시험이 가짜 의존성을 넣도록 `deps` prop을 받는다.

## 다른 유닛에 넘기는 사항과 남은 결정

- **통합 검증 명령(NFR7.2, F13)**: 프로젝트의 Construction Verification Command(`mvn -q verify`)는 이 유닛의 화면 시험을 실행하지 않는다. Unit 체크포인트에서 `mvn -q verify`와 `npm --prefix services/u4-web-ui run verify`를 함께 실행하는 명령을 제안하고 사람의 승인을 받는다.
- **u5-local-run**: `npm --prefix services/u4-web-ui run dev`로 기동한다(포트 5173 고정, 쓰이는 중이면 실패). 필요하면 `U1_URL`·`U2_URL`·`U3_URL`을 준다. 기동 순서는 u1 → u2 → u3 → u4.
- **시연 절차(F14)**: 결제 대기 중 탭은 하나로 쓴다(브라우저가 같은 오리진에 동시 연결을 6개로 제한하는데 SSE 구독이 하나를 차지한다). 시연 설정(모의 결제 결과와 지연 초 1~600)은 화면 하단 접힌 영역에 있다.
- **Build and Test**: 실제 브라우저 확인 절차(nfr-design/performance-design.md의 L3)와 웹훅 수신부터 화면 표시까지 1초 합산 측정(100건 반복 최대 1초)을 한다.

## 알려진 사항

- 인메모리 서비스 재시작이나 서비스 장애로 예약이 사라지면 복구가 404를 보고 S1로 돌아간다. 60초 안에 복구되지 않으면 새로 고침 안내를 보이고 멈춘다.
- 이 머신에는 다른 프로젝트의 vite 서버가 5173 등을 쓰고 있을 수 있어 `dev`가 실패할 수 있다. 개발자 에이전트는 시험에서 임의 포트만 썼고 남은 백그라운드 프로세스는 없다.

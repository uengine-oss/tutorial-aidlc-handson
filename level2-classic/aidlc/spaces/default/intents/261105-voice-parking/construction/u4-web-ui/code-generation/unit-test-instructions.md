# u4-web-ui 단위 시험 안내

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] code-generation-plan.md, nfr-requirements/tech-stack-decisions.md(Vitest, @vue/test-utils, jsdom, v8 커버리지), nfr-design/performance-design.md(시험 계층 L1·L2·L3, 판정 통계, 스크립트), team.md(TDD, 80% 하한)

## 사전 준비

- Node 25.x, npm 11.x (이 머신에 설치됨). 의존성을 내려받으려면 네트워크 접근이 필요하다(`npm install`은 Step 1에서 한 번).
- Docker는 쓰지 않는다. 다른 서비스를 띄울 필요가 없다. 서비스 호출은 시험에서 가짜 `fetch`와 가짜 `EventSource`로 대체하고, 프록시 통합 시험만 시험용 서비스 서버를 임의 포트로 직접 띄운다.

## 이 유닛의 시험 실행 명령

저장소 루트에서 실행한다. 이 유닛에만 한정된 명령이다.

```
npm --prefix services/u4-web-ui run test:unit
```

한 파일만 실행하려면 `-- <파일 경로>`를 덧붙인다 (예: `npm --prefix services/u4-web-ui run test:unit -- src/composables/useApp.test.js`).

시간 시험과 프록시 통합 시험까지 포함한 전체 시험(순차 실행):

```
npm --prefix services/u4-web-ui run test
```

## 커버리지

- 목표: 라인 커버리지 80% 이상 (팀 규칙 하한, 낮추지 않는다). `test:coverage`는 `unit` 프로젝트만 계측한다. 시간을 재는 시험은 계측이 없는 `test`에서만 판정한다.
- 커버리지 임계값은 시험이 모두 통과해도 하한 미달이면 종료 코드가 0이 아니게 만든다. 그래서 시험 명령과 따로 실행한다.

```
npm --prefix services/u4-web-ui run test:coverage
```

- 범위: `src/**`와 `vite.proxy.js`. 제외는 `src/main.js`, 시험 파일뿐이다.
- 프로젝트 전체 하한은 Build and Test가 전체 시험으로 확인한다. 이 유닛 단독 실행의 하한 결과는 참고용이며, 선택된 시험이 모두 통과하고 하한만 미달이면 시험 실패가 아니라 그 사실을 기록한다.

## 병합 전 검증

```
npm --prefix services/u4-web-ui run verify
```

`lint` → `test` → `test:coverage` → `build` → `audit:report` 순서로 실행한다. 앞의 네 단계 중 하나라도 실패하면 실패한다. `audit:report`는 보고 전용이다. 프로젝트의 통합 검증 명령에 이 명령을 묶는 일은 Unit 체크포인트에서 사람이 승인한다.

## 시험 구성 (약 90개)

| 시험 | 대상 | 확인하는 것 |
|---|---|---|
| guards.test.js | 응답 형식 확인 | 추천·스냅샷·오류 본문 정상, 알 수 없는 status·정수 아닌 version·필드 누락 거부 |
| format.test.js | 표시 형식 | 천 단위 요금, "N시간 N원" |
| useSession.test.js | 세션과 저장소 | UUID 생성·유지, 저장소 불가 시 메모리 유지, 대체 UUID, 추천 목록 저장·복원·삭제 |
| api.test.js | 요청 래퍼 | 상대 경로, 헤더, 3초 제한, 오류 본문, 구독 URL의 sessionId, 세션 ID 로그 비기록 |
| useApp.test.js | 화면 상태 | 추천(S2·S3·S4), 결제 응답별 처리, 멱등 키 규칙, 새 검색, 복원, 새로 시작, 경과 시간 |
| useReservationStream.test.js | 구독 | version 필터, 구독 교체·종료·pagehide, 깨진 데이터 무시, 복구(물러나기·한도·404·401) |
| 컴포넌트 시험 7개 | QueryInput, ParkingCard, RecommendationList, ReservationStatus, ErrorNotice, EmptyNotice, ScenarioPanel | props·이벤트·접근성 속성·testid |
| App.test.js | 두 영역 조합 | aria-live 사건, 포커스 규칙, 서버 문자열의 텍스트 렌더링 |
| vite.proxy.test.js | 프록시 설정 값 | 경로·대상·타임아웃·`Accept-Encoding`, `bypass`, 환경 변수, `assertLoopbackHost` 값 목록 |
| security.source.test.js | 소스 검색 | 금지 API와 비밀 값 패턴 0건, 패턴의 자체 확인 |
| *.timing.test.js | 시간 (L1) | 이벤트→DOM 100ms, 응답→카드 100ms, 틱 5ms, 복구 간격(가짜 시계) |
| *.proxy.test.js | 프록시 통합 (L2) | 실제 Vite 서버: 웹훅·`/internal` 수신 0, 정상 경로 도달, CLI `--host` 거부, 포트 사용 중 실패, SSE 전달 지연·묶임·`Content-Encoding` 없음 |

## 모의·시험 데이터

- `fetch`: 시험마다 응답 큐를 가진 가짜(상태 코드와 본문, 지연, 무응답, 네트워크 오류).
- `EventSource`: 이벤트를 직접 내보내고 오류·재연결·`readyState`(CONNECTING, CLOSED)를 흉내 내는 가짜.
- 시계: 주입하는 `now()`와 Vitest 가짜 타이머.
- 프록시 시험용 서버: 임의 포트(0)로 뜨는 시험용 u1·u2·u3 서버(받은 요청을 센다)와 시험용 SSE 서버.
- 시간 판정 규칙: 예열 20회 버리고 100회, 출력에 중앙값·p99·최댓값. p99가 한도 이하이고 최댓값이 한도의 2배 이하(틱은 4배)다. 재실행으로 가리지 않는다.

# u4-web-ui 보안 설계

## Sources

- [scope] Workflow-selected scope: classic
- [Q4] 로그인 없음, 익명 세션 (requirements-analysis Q4)
- [memory:M1] nfr-requirements/security-requirements.md(NFR6.1~NFR6.7, NFR8.1), functional-spec.md(W1, W3, 서비스 연결), frontend-components.md, contract-summary.md(C3의 세션 쿼리 가정), u3-payment-service의 security-design.md(프록시 필수 의무), nfr-requirements 검토에서 넘어온 항목

| 설계 | 방식 | 충족하는 요구사항 |
|---|---|---|
| 텍스트로만 렌더링 | 서버가 준 모든 문자열은 템플릿의 `{{ }}` 보간(Vue가 이스케이프)으로만 그린다. 금지 API: `v-html`, `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, 렌더 함수의 `domProps: { innerHTML }`. 이중으로 막는다: (1) ESLint 규칙 `vue/no-v-html`을 오류로, `no-restricted-properties`와 `no-restricted-syntax`로 위 속성·메서드를 오류로 둔다. (2) 소스 검색 시험(`src/**`에서 금지 API 문자열 0건). 서버 응답에 `<script>`, `<img onerror>`를 넣은 컴포넌트 시험으로 DOM에 요소가 생기지 않고 글자로만 보임을 확인한다. | NFR6.1 |
| 비밀 값 없음 | 화면 코드와 설정에 키·토큰을 두지 않는다. 비밀 값 패턴 검색 시험(`src/**`, 설정 파일; 시험 소스는 제외)을 둔다. 웹훅 비밀 값은 화면이 모른다. | NFR6.2 |
| 결제 프록시 정확 일치 | `vite.proxy.js`가 프록시 설정을 값으로 내보낸다. 결제 경로 키는 `^/api/payments$`이고 `bypass(req)` 함수가 메서드가 `POST`가 아니거나 요청 URL에 쿼리 문자열이 있으면 `false`를 돌려준다. Vite에서 `false`는 프록시하지 않고 404로 응답하는 것이다(`null`·`undefined`·`true`는 그대로 프록시하므로 쓰지 않는다). 프록시하려면 `bypass`가 `undefined`를 돌려준다. `/api/payments/webhook`, 끝 슬래시, `GET /api/payments`, 쿼리가 붙은 경로는 키에 걸리지 않거나 `bypass`에서 걸러져 u3로 가지 않는다. `/internal/**`에는 키를 두지 않는다. | NFR6.3 |
| 웹훅 차단의 합격 기준 | 합격 기준은 상태 코드가 아니라 **시험용 서비스가 받은 요청 수**다(Vite가 SPA 대체 응답으로 200을 줄 수 있으므로 상태 코드로 판정하지 않는다). 시험은 실제 Vite 서버를 `createServer`로 띄우고 시험용 u1·u2·u3 서버를 뒤에 둔다. 웹훅 경로, 끝 슬래시, `GET /api/payments`, 쿼리가 붙은 `/api/payments`는 시험용 u3가 받은 요청 수 0이고, `/internal/**`(예: `/internal/reservations`, `/internal/passes/x`)는 시험용 u1·u2·u3 모두 받은 요청 수 0이다. 반대로 `POST /api/payments`는 시험용 u3에 1건 닿고, `/api/search/recommendations`와 `/api/reservations/current`는 각각 시험용 u1·u2에 닿는다. | NFR6.3 |
| 세션 ID 처리 | 세션 ID는 `useSession`이 만들고 보관한다. `api` 모듈이 모든 fetch에 `X-Session-Id` 헤더를 붙이고, EventSource만 쿼리 `sessionId`를 쓴다(구독 URL 만드는 함수 하나에서만). 화면 텍스트, 콘솔 출력, 오류 문구에 세션 ID를 넣지 않는다. `crypto.randomUUID`가 없으면 `crypto.getRandomValues`로 UUID v4를 만든다(비보안 컨텍스트 대비). | NFR6.4 |
| 경계 검증 | 입력 텍스트는 앞뒤 공백 제거 후 1~200자(`maxlength` 200 + 공백만이면 전송 비활성), 시연 설정 지연 초는 1~600 정수로 맞춘다. 서버 응답은 `isRecommendation`, `isReservation`, `isApiError` 같은 작은 형식 확인 함수로 필드 존재와 형식을 검사한 뒤 쓴다. 알 수 없는 `status` 값, 정수가 아닌 `version`, 구독 중인 예약과 다른 `reservationId`의 스냅샷, JSON 해석에 실패한 이벤트 데이터(`try/catch`로 버림)는 화면 상태를 바꾸지 않고 콘솔 경고만 남긴다(세션 ID 없이). | NFR6.5 |
| 루프백 바인딩 | 호스트 검사는 설정 파일 안의 환경 변수 읽기가 아니라 Vite가 설정을 해석한 뒤의 값에 건다: `vite.config.js`의 작은 플러그인이 `configResolved(config)`에서 `config.server.host`를 검사한다(CLI의 `--host`가 설정 파일 값을 덮어써도 해석 뒤 값이 검사된다). 검사 로직은 순수 함수 `assertLoopbackHost(host, env)`로 `vite.proxy.js`에 두고 값 목록(`127.0.0.1`, `localhost`, `::1`과 `[::1]`, 대소문자 무시)으로 시험한다. 루프백이 아니면 시작을 거부한다(환경 변수 `UI_ALLOW_NON_LOOPBACK=1`을 명시했을 때만 경고와 함께 허용). 기본 호스트는 환경 변수 `UI_HOST`(기본 `127.0.0.1`)이고 `server.strictPort: true`로 포트 5173을 고정한다. Vite의 `server.allowedHosts`는 기본값을 유지하고 `true`로 열지 않는다. | NFR6.6 |
| 의존성 | `package-lock.json`을 커밋한다. `audit:report` 스크립트가 높음·치명 취약점을 출력한다(종료 코드는 0, 보고 전용). 판단 기준: 높음·치명 취약점이 있으면 그 목록을 이 유닛의 `code-summary.md`와 Unit 체크포인트 보고에 기록하고 사람이 계속 진행할지 정한다. 개발 서버 전용 프로토타입이라 자동으로 막지 않는다. 레지스트리에 닿지 못하면 "audit 불가"를 출력한다. | NFR6.7 |
| 주소는 환경 변수 | 프록시 대상 u1·u2·u3 주소는 `U1_URL`, `U2_URL`, `U3_URL`(기본 http://127.0.0.1:8081, 8082, 8083)로 바꿀 수 있고 기본값만 코드에 둔다. | NFR8.1 |

## 대안

- 별도 포트로 웹훅을 열지 않는 이유는 u3의 설계와 같다(서명 검증이 마지막 방어선).
- CSP·보안 헤더: 프로덕션 번들을 배포하지 않아 적용하지 않는다.

## 시험

- 금지 API 소스 검색, ESLint 규칙 확인, 악성 문자열 렌더링.
- 프록시: 위 합격 기준(실제 Vite 서버).
- 호스트 검사: `assertLoopbackHost`의 값 목록 시험, 루프백이 아닌 `UI_HOST`는 거부, `UI_ALLOW_NON_LOOPBACK=1`이면 경고와 함께 허용, 실제 Vite를 CLI `--host`(비루프백)로 시작하면 거부(통합 시험), 이미 쓰는 포트 5173이면 조용히 다른 포트로 올라가지 않고 실패.
- 응답 형식 확인: 알 수 없는 status, 필드가 빠진 스냅샷, 정수가 아닌 version, 다른 reservationId의 스냅샷, 깨진 JSON 이벤트 데이터는 무시하고 처리기가 멈추지 않음.
- 구독 닫기: 구독 교체 때 기존 구독을 먼저 닫음, `pagehide`에서 닫음.

## Assumptions & Open Questions

- [assumption] 개발 서버 전용이므로 프로덕션 보안 헤더와 요청 속도 제한은 두지 않는다 (NFR8).
- [assumption] `npm audit`의 높음·치명 취약점은 자동 실패가 아니라 사람이 판단한다. 개발 의존성 취약점이 대부분이고 프로덕션 번들을 배포하지 않는다.
- 열린 질문: 없음.

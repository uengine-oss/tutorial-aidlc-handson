# u4-web-ui 기술 스택 결정

## Sources

- [scope] Workflow-selected scope: classic
- [Q2] UI 컴포넌트 라이브러리 없음, 직접 작성한 CSS (refined-mockups Q2)
- [memory:M1] team.md(프런트 Vue 3, ESLint + Prettier, TDD, 커버리지 80%, Docker 금지, 로컬 실행 스크립트), requirements.md의 NFR4, NFR7, NFR8, NFR9, functional-spec.md, frontend-components.md, performance-requirements.md, security-requirements.md

이 머신에서 확인한 도구: Node 25.2.1, npm 11.6.2, Java 17, Maven 3.9.12. 프런트는 Maven 모듈이 아니라 `services/u4-web-ui/`의 독립 npm 프로젝트다.

| 영역 | 선택 | 이유 | 요구사항 |
|---|---|---|---|
| 언어 | JavaScript(ES 모듈, `<script setup>`), 타입은 JSDoc | 팀 규칙에 TypeScript가 없고 작은 단일 페이지라 타입 도구를 더하지 않는다 | NFR4, NFR9 |
| 프레임워크 | Vue 3 (Composition API, 단일 파일 컴포넌트) | 팀 규칙 | NFR4 |
| 빌드·개발 서버 | Vite (개발 서버 프록시 사용, 프로덕션 빌드는 컴파일 확인용으로만 실행) | 계약이 정한 프록시 방식(`/api/*`)과 맞고 SSE를 그대로 전달할 수 있다. 배포하지 않는다 | NFR4, NFR8 |
| 라우터·상태 관리 | 쓰지 않음 | 한 페이지이고 상태는 `useApp` composable 하나가 소유한다 | NFR4 |
| 스타일 | 직접 작성한 CSS(CSS 변수 토큰) | 사용자가 라이브러리 없음을 선택했다 | Q2 |
| 시험 | Vitest, `@vue/test-utils`, jsdom, 가짜 `fetch`와 가짜 `EventSource` | TDD와 컴포넌트·composable 시험에 충분하다. 프록시 통합 시험은 Node 환경의 Vitest로 실제 Vite 서버를 띄워 확인한다 | NFR7 |
| 커버리지 | `@vitest/coverage-v8`, 라인 커버리지 80% 하한(임계값 설정) | 팀 규칙. 미달이면 시험 명령이 실패한다 | NFR7 |
| 포매터·린터 | ESLint(flat config, `eslint-plugin-vue`)와 Prettier | 팀 규칙. 린트·포맷 검사를 `npm run lint`로 실행 | NFR9 |
| 의존성 고정 | `package-lock.json` 커밋 | 같은 버전으로 재현하고 audit 가능 | NFR6.7 |
| 실행 | `npm run dev` (포트 5173, 루프백). 프록시 대상 주소는 환경 변수 `U1_URL`, `U2_URL`, `U3_URL`(기본 http://127.0.0.1:8081, 8082, 8083)로 바꿀 수 있다. u5-local-run이 한곳에서 묶는다 | 팀 규칙(로컬 실행 스크립트, Docker 금지) | NFR8 |

버전: 코드 생성 단계에서 npm 레지스트리의 사용 가능한 최신 안정 버전을 쓰고 잠금 파일로 고정한다(Vue 3.x, Vite, Vitest, ESLint, Prettier의 서로 호환되는 조합).

## 요구사항

| ID | 요구사항 | 측정 방법 | 출처 |
|---|---|---|---|
| NFR7.1 | 각 계층마다 실패하는 테스트를 먼저 쓴 뒤 구현한다(TDD). 라인 커버리지는 Vitest(v8)로 측정해 80% 이상이어야 하며 하한 미달이면 시험 명령이 실패한다. 하한은 약화하지 않는다. 병합 전에 시험과 커버리지 검사, 린트를 실행한다. | `npm run test:coverage` 통과 | NFR7, team.md Testing Posture |
| NFR7.2 | 이 유닛의 시험은 Maven으로 실행되지 않으므로, 프로젝트의 Construction Verification Command(현재 `mvn -q verify`)가 화면 시험도 실행하도록 통합 검증 명령을 정해야 한다. 코드 생성 후 Unit 체크포인트에서 사람이 승인하는 명령으로 정한다(예: Maven 시험과 `npm run verify`를 함께 실행하는 루트 스크립트). | 체크포인트에서 승인된 명령이 화면 시험까지 실행함 | NFR7, 체크포인트 규칙 |
| NFR9.1 | 포매터와 린터는 ESLint와 Prettier를 따른다. 컴포넌트 파일은 PascalCase, 변수와 함수는 camelCase, composable은 `use` 접두사다. | `npm run lint` 통과 | NFR9, team.md Code Style |
| NFR8.2 | Docker를 쓰지 않는다. 화면은 `npm run dev` 한 줄로 로컬에서 기동되며 프로덕션 번들을 배포하지 않는다. | 실행 확인 | NFR8, project.md Forbidden |
| NFR5.1 | 결제 요청의 재시도는 서비스 쪽 멱등 키로 안전해야 한다. 화면은 응답을 받지 못한 시도의 멱등 키를 보관해 같은 후보를 다시 누를 때 같은 키로 보내고, 확정된 응답(202, 200, 409, 404, 422)을 받으면 지운다. 보관 키는 passId와 durationHours가 같을 때만 재사용한다. | 가짜 fetch로 재시도는 같은 키, 새 후보·다른 이용 시간·확정 응답 뒤는 새 키임을 확인 | NFR5, functional-spec.md UR3.2 |
| NFR4.1 | 화면은 독립 프런트엔드 프로젝트이며 백엔드 서비스와는 HTTP(프록시)로만 소통한다. 서비스 코드를 직접 import하지 않는다. | 구조 확인 | NFR4 |

## 대안

- TypeScript + `vue-tsc`: 팀 규칙에 없고 규모가 작아 선택하지 않았다. 필요하면 후속 변경이다.
- Playwright 등 브라우저 E2E: 전체 흐름 확인은 Build and Test의 통합 실행 몫이고 이 유닛의 단위 시험은 Vitest + jsdom으로 한다. 개발 서버 프록시와 SSE 통과만 실제 Vite 서버로 확인한다.
- Pinia·Vue Router: 한 페이지·한 composable이라 선택하지 않았다.

## Assumptions & Open Questions

- [assumption] npm 레지스트리에서 의존성을 내려받을 수 있는 네트워크 접근이 있다.
- [assumption] jsdom에는 `EventSource`가 없으므로 시험은 가짜 `EventSource`를 쓰고, 브라우저의 실제 자동 재연결 동작(CLOSED 포함)은 가짜가 흉내 낸다. 실제 브라우저 동작은 Build and Test의 통합 실행에서 확인한다.
- 열린 질문: NFR7.2의 통합 검증 명령은 코드 생성 뒤 Unit 체크포인트에서 사람이 승인한다.

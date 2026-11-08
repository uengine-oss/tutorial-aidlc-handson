<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
- 2026-11-08T00:00:00Z — u5 NFR 설계 검토(READY) 남은 항목, 코드 생성 계획에서 반드시 닫는다: (Major) 커버리지 분모 규칙 완결(함수 머리줄·case 라벨·이어진 줄·여러 줄 명령 LINENO, 한 명령 한 줄 작성 규칙, 제외 규칙은 coverage.sh 한곳, 구현 첫 시험으로 3.2 줄 번호 보존 확인), (Major) SHELLOPTS=xtrace와 비밀 값 비출력·로그 시험 충돌 → 비밀·로그·권한 시나리오는 xtrace 없이 별도 실행, start_service에서 env -u SHELLOPTS로 서비스 상속 차단, (Minor) 설계가 상류 NFR8.9·준비 확인 행·커버리지 행을 대체함을 명시하고 세션 헤더 이름·UUID 생성법, 공유 계약 C1의 400 확인, (Minor) 웹훅 미도달 판정은 상태 코드 404/405가 주, 로그 비교는 보조, /internal은 u2 로그로 따로 비교, (Minor) 주입 변수 표 이름 전체를 -u로 먼저 제거, 비밀 값은 env 인자가 아닌 export로 전달, 사용자 비밀 32자 근거와 u3 최소 길이 대조, (Minor) 그룹 ID 취득 방식, 정리 중 신호 무시 구간, 두 번째 Ctrl-C 합격 기준.
- 2026-11-07T16:30:00Z — u4 NFR 설계 2차 검토(READY) 남은 Minor, 코드 생성 계획으로 닫는다: L2 시간 시험의 프로젝트 소속(proxy 프로젝트의 시간 시험은 순차)과 timing·proxy 프로젝트의 직렬 실행, assertLoopbackHost 값 목록에 true·0.0.0.0·undefined 추가, vite.config.js를 coverage 제외 목록에 명시하고 vite.proxy.js가 unit 시험만으로 80%를 넘는지 확인, 금지 API 목록의 domProps는 Vue 2 문법이라 Vue 3 형태(h()의 innerHTML 속성, v-bind 객체)로 정정, 비밀 값 패턴 검색의 패턴·대상·예외 정의, L2 시험 서버 포트는 임의 포트(0)를 쓰고 개발 서버 5173과 충돌하지 않게.
- 2026-11-07T13:55:00Z — u3 NFR 설계 2차 검토(READY) 남은 항목, 코드 생성 계획의 결정으로 닫는다: performance-design.md 웹훅 흐름도의 Payment 잠금 위치는 본문대로 u2 호출 앞이 맞다(흐름도가 낡음), PG_CALLBACK_NO_RESPONSE 원인 코드를 관측성 로그에 포함, 의존 방향 시험은 webhook 패키지도 훑고 pg를 import할 수 있는 곳은 config뿐, u2 호출은 요청 전체 총 소요 시간 제한(호출 단위 timeout)을 쓰고 느리게 흘리는 가짜 u2 시험 추가, 웹훅의 Payment 잠금 대기 상한 또는 잔여 위험 명시, 성능 시험 재실행 규칙은 JVM 정지 확인 시에 한해 1회로 좁힌다, 프록시 의무는 code-summary로 u4·u5에 전달.
- 2026-11-07T12:35:00Z — u2 NFR 설계 2차 검토(READY) 남은 항목: (Major) 막힌 쓰기 스레드가 풀리지 않을 때 직접 인계 실행기의 RejectedExecution 동작과 스레드 상한(구독 상한의 2배 등) 미정, (Minor) logical-components.md의 u1 시도 제한 500ms를 350ms로 정정, 구독 상한 503 재연결 정책과 C3 반영. 검토가 끝난 파일이라 고치지 않았고 Code Generation 계획에서 반영한다. 첫 검토의 Major 3건과 NFR 요구 단계의 Major 2건은 설계에 반영됨.
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

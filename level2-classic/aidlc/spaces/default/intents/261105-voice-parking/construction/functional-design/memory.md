<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-11-07T12:20:00Z — u2 기능 설계: 질문 없이 진행했다. 앞선 단계(요구사항·계약·컴포넌트)가 경계 규칙을 이미 정했고 u1도 질문 없이 끝났다. 남는 판단은 모두 [assumption]으로 functional-spec.md에 적었다.
- 2026-11-07T12:20:00Z — 180초 경계를 타이머가 아니라 시계로 판정하는 규칙(BR2.3)으로 만들었다. 타이머 지연(±2초)이 AC4.2.3(181초 승인은 거부)을 깨뜨릴 수 있어서다.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-07T12:20:00Z — components.md는 lastSnapshot을 Subscription 속성으로 두었으나 연결보다 오래 살아야 하므로 예약 단위 LatestSnapshot으로 분리했다(Notification 소유 유지).

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-07T12:20:00Z — 인메모리 저장소 선택: 재시작 시 예약·타이머가 사라지지만 프로토타입·로컬 실행 제약에 맞고 설계가 단순하다.

## Open questions
- 2026-11-07T16:00:00Z — u4 기능 설계 2차 검토(READY) 남은 항목, 코드 생성 계획의 결정으로 닫는다: (Major) 시간 예산 배분(계약 C3는 프록시+화면 합 200ms인데 설계가 화면 200+프록시 100=300ms로 적음 → 화면 100ms + 프록시 100ms로 다시 나눈다), (Major) 프록시 시험은 실제 Vite createServer로 vite.proxy.js를 읽어 띄워 웹훅 차단과 SSE 전달을 확인(독립 http-proxy는 bypass·정규식을 실행하지 않음), (Minor) 최댓값 판정의 부하 민감성(u3와 같은 유형, 예열·재실행 정책 명시, 수치는 낮추지 않음), 보관 시도에 durationHours 포함하고 400·401 처리, S7→S1 전이와 409·404 행을 전이표에, 복구 안내 문구 3개를 오류 표와 aria-live 목록에 포함, 시연 설정 지연 초 범위 1~600으로 통일.
- 2026-11-07T13:40:00Z — u3 기능 설계 검토(READY, 조언) 남은 항목, 코드 생성 계획의 결정으로 닫는다: (Major) 같은 (sessionId, idempotencyKey) 동시 요청은 키별 원자 처리(Payment 조회·생성과 모의 PG 요청 한 번), (Major) 이미 종료된 Payment에 새 eventId 웹훅이 오면 u2 REJECTED여도 Payment.status는 유지하고 WebhookEvent만 IGNORED 기록, (Minor) u2 저하 시 150ms 예산 밖(최악 약 6.4초)을 받아들임, 모의 PG 시계·웹훅 주소 설정 키·자기 호출 실패 로그, 웹훅 본문 상한, 키 재사용 불일치는 기존 결과 반환.
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-11-07T12:25:00Z — 검토(READY, 조언)에서 남은 Major 2건: (1) BR1.2의 멱등 재요청 200 응답 status가 C5의 PENDING_PAYMENT enum과 충돌, 종료된 예약일 때 u3 동작 미정 (2) 같은 키 동시 요청 시 BR1.2를 BR1.3보다 먼저 검사한다고 W1에 명시 필요. Minor: 결제 결과 400 검증, 구독 등록·스냅샷 읽기 순서, 카디널리티, cancelledAt 기준. Code Generation 계획에서 처리하거나 변경 요청으로 반영한다.

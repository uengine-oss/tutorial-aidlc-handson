# Architecture Decision Records

## ADR-001: 컴포넌트를 5개로 나눈다

- **Context**: 요구사항은 입력 해석, 추천, 결제, 예약, 실시간 알림이라는 서로 다른 변경 이유를 가진 기능을 요구한다 (FR1~FR7). 팀 규칙은 모듈이 여럿이면 마이크로서비스로 나누도록 정했다 (team-practices.md).
- **Decision**: QueryParser, Recommendation, Reservation, Payment, Notification의 5개 컴포넌트로 나눈다 ([Q1] 사용자 선택). 서비스로 묶는 방식은 Units Generation에서 정한다.
- **Consequences**: 변경 이유별로 독립적으로 시험하기 쉽고 마이크로서비스 후보가 분명해진다. 컴포넌트 간 호출과 계약이 늘고, 시험할 상호작용이 많아진다.
- **Alternatives Rejected**: 3개 통합안(Search, Booking, Notification)은 결제 웹훅과 180초 타이머가 한 덩어리가 되어 경계 동시성 시험이 복잡해져서 선택하지 않았다.

## ADR-002: 예약 상태의 확정은 Reservation 한 곳에서만 한다

- **Context**: 180초 경계에서 승인 웹훅과 자동 취소가 겹칠 수 있고, 승인 웹훅은 중복해서 올 수 있다 (FR5.3, FR5.4, FR7.1, NFR5). 요구사항 검토에서 경계 규칙이 모호하다고 지적되었다.
- **Decision**: 예약의 상태 전이(결제 대기에서 예약 완료, 결제 실패, 취소)는 Reservation이 소유하고 한 번에 하나만 확정한다. 결제 결과 반영과 자동 취소는 모두 Reservation의 원자 연산이며, 예약이 아직 결제 대기일 때만 "적용됨"이 되고 이미 확정되었으면 "거부됨"을 돌려준다. 먼저 확정된 쪽이 이기며 거부된 승인은 되살리지 않는다. Payment는 웹훅 이벤트 ID로 중복을 거르고 Reservation에 결과를 전달하며, 돌려받은 "거부됨"이면 무시했다고 기록한다. Payment.status는 PG가 알려 준 결과의 기록이고, 사용자에게 보이는 예약 상태의 기준은 Reservation.status다.
- **Consequences**: 경계 동시성 시험을 Reservation 한 곳에서 할 수 있다. Reservation이 병목이자 핵심 시험 대상이 된다.
- **Alternatives Rejected**: Payment와 Reservation이 각자 상태를 가지는 방식은 두 곳이 어긋나 예약이 완료와 취소로 동시에 보일 수 있어 선택하지 않았다.

## ADR-003: 호출 방향을 Payment에서 Reservation으로 둔다

- **Context**: 결제 요청 시 대기 예약이 필요하고, 승인·실패 결과가 예약 상태를 바꾼다. 양방향 호출은 순환 의존을 만든다.
- **Decision**: Payment가 Reservation을 호출한다 (대기 예약 생성, 결과 반영). Reservation은 Payment를 호출하지 않는다. Reservation은 Notification에 상태 스냅샷을 이벤트로 밀어 넣고 Notification은 Reservation을 호출하지 않는다.
- **Consequences**: 의존 그래프가 순환 없이 단방향이다. 자동 취소 뒤 늦게 온 승인은 Payment가 예약 상태를 미리 읽는 것이 아니라 Reservation 원자 연산이 돌려준 "거부됨"으로 알게 되므로, 읽기와 확정 사이의 경합이 없다. 서버 재시작으로 스냅샷이 사라지면 화면이 Reservation에서 직접 조회해야 한다.
- **Alternatives Rejected**: Reservation이 Payment를 호출하는 방식은 결과 반영에서 순환이 생겨 선택하지 않았다.

## ADR-004: 알림은 SSE 구독을 관리하는 별도 컴포넌트로 둔다

- **Context**: 승인 웹훅 수신 후 1초 안에 화면에 예약 완료를 보여야 하고 (NFR1), 연결이 끊긴 뒤에도 현재 상태를 다시 받을 수 있어야 한다 (FR6.3).
- **Decision**: Notification이 SSE 구독을 관리한다. Reservation이 상태 스냅샷을 밀어 넣으면 해당 구독에 푸시하고, 재구독 시 보관한 가장 최근 스냅샷을 먼저 보낸다. Notification은 Reservation을 호출하지 않는다. 구체적인 SSE 구현 방식은 이후 기능·NFR 설계에서 정한다.
- **Consequences**: 비즈니스 규칙과 연결 관리가 분리된다. 구독 상태가 서버 메모리에 있으면 재시작 시 사라지는 한계가 있다.
- **Alternatives Rejected**: 화면이 폴링으로 조회하는 방식은 1초 이내 목표를 안정적으로 맞추기 어렵고, WebSocket은 단방향 푸시에 비해 이 용도에 과해 선택하지 않았다 (SSE는 [Q6] 사용자 선택).

## ADR-005: 모의 PG는 외부 의존성으로 본다

- **Context**: 모의 PG는 승인, 지연, 실패 시나리오를 재현하는 도구이며 실제 결제 연동은 하지 않기로 했다 (Q2, FR5.5).
- **Decision**: 모의 PG를 비즈니스 컴포넌트로 만들지 않고 Payment의 외부 의존성(third-party-api)으로 둔다. 띄우는 방식은 Units Generation에서 정한다.
- **Consequences**: 컴포넌트 수가 늘지 않고 실제 PG로 바꿀 때 Payment 안에서만 바꾸면 된다. 모의 PG의 구현 위치가 정해지지 않은 채 남는다.
- **Alternatives Rejected**: 모의 PG를 여섯 번째 컴포넌트로 두는 방식은 실제 연동 때 사라질 도구를 비즈니스 경계로 올리게 되어 선택하지 않았다.

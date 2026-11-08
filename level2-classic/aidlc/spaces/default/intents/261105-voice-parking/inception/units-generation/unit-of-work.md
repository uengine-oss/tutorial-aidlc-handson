# Units of Work

## Sources

- [desc] Initial description: 사용자가 음성 텍스트('강남역 근처 2시간 주차권 찾아줘')를 보내면 최적 주차권을 추천하고 결제와 예약을 처리하는 VoiceParking 프로토타입. 프런트 Vue 3, 백엔드 Java Spring Boot, 모듈이 여럿이면 마이크로서비스.
- [scope] Workflow-selected scope: classic
- [Q1] 백엔드는 3개 서비스(search, reservation, payment)
- [Q2] 모의 PG는 payment-service 안에 두되 승인·실패 결과를 자기 웹훅 엔드포인트에 HTTP로 비동기 호출
- [Q3] 화면은 Vite 개발 서버 프록시로 각 서비스에 연결 (주소 하나, CORS 불필요)
- [memory:M1] requirements.md의 요구사항 FR1~FR9, NFR1~NFR9 (requirements), components.md의 컴포넌트 5개와 ADR-001~005, team-practices.md (Docker 금지, 별도 배포 환경 금지, 로컬 실행 스크립트)

## Units

| Unit ID | Directory | Kind | Complexity | 담당 컴포넌트 |
|---|---|---|---|---|
| U1 | u1-search-service | service | M | QueryParser, Recommendation |
| U2 | u2-reservation-service | service | L | Reservation, Notification |
| U3 | u3-payment-service | service | L | Payment (내장 모의 PG 포함) |
| U4 | u4-web-ui | ui | M | Vue 3 화면 (components.md의 컴포넌트가 아니라 사용자 화면 S1~S8) |
| U5 | u5-local-run | packaging | S | 로컬 실행 스크립트 |

## U1 u1-search-service

- 책임: 한국어 음성 텍스트에서 장소와 이용 시간을 추출하고, 목업 주차장·주차권 데이터에서 후보를 조회해 종합 점수로 최적 1개와 비교 후보 2개를 이유와 함께 돌려준다. 주차권 조회 기능(예약이 가격과 주차장 정보를 가져가는 용도)도 제공한다.
- 배포 모델: standalone (독립 프로세스, 자체 내장 저장소)
- 제약: 점수 = 가격 0.4 + 거리 0.4 + 시간 적합도 0.2, 후보 간 min-max 0~100 환산, 동점이면 가격이 낮은 쪽 우선.
- 구현 메모: Spring Boot. 목업 데이터는 시작할 때 적재한다.

## U2 u2-reservation-service

- 책임: 예약 상태(결제 대기, 예약 완료, 결제 실패, 취소)의 단일 확정 지점. 결제 결과 반영과 180초 자동 취소를 원자 연산으로 처리하고, 상태 변화를 같은 프로세스 안의 Notification에 스냅샷으로 밀어 넣어 SSE로 화면에 푸시한다. 세션별 예약 조회와 현재 상태 조회를 제공한다.
- 배포 모델: standalone
- 제약: 취소 허용 오차 180초 ± 2초, 세션당 결제 대기 1건, 웹훅 수신부터 화면 표시 1초 이내(NFR1). U1에서 주차권 정보를 조회한다.
- 구현 메모: Reservation과 Notification은 같은 프로세스에 있지만 컴포넌트 경계(패키지)는 분리한다. 구현 순서는 Reservation 상태 전이가 먼저고 Notification이 그 위에 올라간다.

## U3 u3-payment-service

- 책임: 화면의 결제 요청을 받아 U2에 대기 예약을 만들고 모의 PG에 결제를 요청한다. 모의 PG는 승인·지연·실패 시나리오를 고를 수 있으며, 결과를 자기 웹훅 엔드포인트에 HTTP로 비동기 호출한다. 웹훅은 공유 비밀 값으로 검증하고 이벤트 ID로 멱등 처리한 뒤 U2의 결제 결과 반영 연산에 전달한다.
- 배포 모델: standalone. 모의 PG는 같은 프로세스 안에 있다.
- 제약: 비밀 값은 환경 변수로 주입한다 (하드코딩 금지). 잘못된 비밀 값은 거부하고 기록한다. 거부된 승인은 되살리지 않고 무시했다고 기록한다.
- 구현 메모: 모의 PG와 웹훅 수신이 같은 프로세스지만 코드 경계는 나눠서 실제 PG로 바꿀 때 모의 PG 부분만 걷어낼 수 있게 한다. [assumption] 모의 PG가 자기 엔드포인트를 HTTP로 호출하는 것은 외부 PG가 호출하는 것과 같은 보안 검증을 거친다.

## U4 u4-web-ui

- 책임: Vue 3 단일 페이지. 입력, 추천 결과, 결제 승인 대기, 예약 완료, 결제 실패, 자동 취소 화면(S1~S8)과 SSE 구독을 구현한다.
- 배포 모델: Vite 개발 서버로만 실행한다 (프로덕션 빌드 배포 없음). Vite 프록시로 U1~U3에 연결한다.
- 제약: 직접 작성한 CSS, ESLint + Prettier, 데스크톱 + 기본 접근성(Q3 of refined-mockups).
- 구현 메모: SSE가 Vite 프록시를 거치므로 프록시가 이벤트를 버퍼링하지 않는지 확인이 필요하다. [assumption]

## U5 u5-local-run

- 책임: 로컬에서 스크립트 한 번으로 U1~U4를 기동하고, 기동 실패한 서비스를 오류로 알려 준다. Docker를 쓰지 않는다.
- 배포 모델: embedded (저장소 루트의 스크립트)
- 구현 메모: 기동 순서와 포트, 환경 변수(웹훅 비밀 값 포함)는 이 유닛이 한곳에서 정한다.

## Assumptions & Open Questions

- 서비스 간 계약(요청·응답 형식, 오류 형식)은 다음 단계 Contract Design에서 정한다.
- 서비스의 포트 번호와 Vite 프록시 경로 규칙은 Contract Design 또는 Functional Design에서 정한다.
- 위 [assumption] 두 건(모의 PG 자기 호출의 검증 동일성, SSE 프록시 버퍼링)은 사용자 확인 전까지 가정으로 유지한다.

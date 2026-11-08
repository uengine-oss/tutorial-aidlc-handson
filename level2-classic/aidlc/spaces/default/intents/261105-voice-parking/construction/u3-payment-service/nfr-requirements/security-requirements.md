# u3-payment-service 보안 요구사항

## Sources

- [scope] Workflow-selected scope: classic
- [Q4] 로그인 없음, 브라우저 세션 단위 익명 사용자 (requirements-analysis Q4)
- [memory:M1] requirements.md의 NFR6, NFR8, rules.md의 BR1.1, BR3.1~BR3.3, contract-summary.md(공통 규칙, C2, C6), phases/construction.md(보안: 입력 검증, 비밀 값 하드코딩 금지, 인증·인가 우회 표시), u2-reservation-service의 security-requirements.md

| ID | 요구사항 | 측정 방법 | 출처 |
|---|---|---|---|
| NFR6.1 | 웹훅은 출처와 무관하게 본문 원문 바이트에 대한 HMAC-SHA256 서명을 검증한다. 키는 환경 변수 `PAYMENT_WEBHOOK_SECRET`이다. 헤더 `X-Signature`가 없거나 `sha256=<소문자 hex>` 형식이 아니거나 값이 다르면 401 INVALID_SIGNATURE이며 상태를 바꾸지 않는다. 비교는 상수 시간으로 한다. 서명 검증은 본문을 해석하기 전에 한다. | 맞는 서명, 틀린 서명, 서명 없음, 형식 오류, 본문 변조 시험 | NFR6, BR3.1 |
| NFR6.2 | 비밀 값은 코드와 설정 파일에 두지 않는다. 환경 변수가 없거나 비어 있으면 서비스는 기동을 거부한다. 모의 PG도 같은 값으로 서명한다. | 환경 변수 없이 기동 시험에서 기동 실패, 소스·설정에서 비밀 값 패턴 검색 결과 0건 | NFR6, BR3.2 |
| NFR6.3 | 서명 거부 사실은 시각, 본문 길이, 이유와 함께 로그에 남기고 본문 내용과 서명 값은 남기지 않는다. 승인 무시(IGNORED)와 중복도 결제 ID, 예약 ID, eventId, outcome과 함께 로그에 남긴다. | 로그 출력 시험(본문·서명 값 비기록) | NFR6, BR3.1, BR3.5 |
| NFR6.4 | 모든 입력은 시스템 경계에서 검증한다. `X-Session-Id`는 UUID, `passId`와 `idempotencyKey`는 비어 있지 않고 100자 이하, `durationHours`는 1~24, `mockScenario`는 APPROVE·DELAY·FAIL, `mockDelaySeconds`는 0~600, 웹훅의 `eventId`는 1~100자, `outcome`은 APPROVED·FAILED, `occurredAt`은 날짜 시각이어야 한다. 어긋나면 400을 돌려주고 아무것도 바꾸지 않는다. | 잘못된 입력별 단위 시험 | NFR6, BR1.1, BR3.3 |
| NFR6.5 | 웹훅 요청 본문은 8KB를 넘으면 읽지 않고 400 INVALID_REQUEST로 거부한다. 서명 검증을 위해 임의 크기를 읽지 않는다. | 8KB 초과 본문 시험 | NFR6, BR3.1 |
| NFR6.6 | 인증은 없다. 세션 ID는 식별용 익명 UUID이며 이 서비스는 결제 요청에 쓰기만 하고 세션별 조회를 제공하지 않는다. 인증을 우회하는 코드를 만들지 않는다. | 설계 확인 | NFR6, Q4 |
| NFR6.7 | 서비스는 루프백 주소에만 연다(`server.address` 기본 `127.0.0.1`). 웹훅 경로(`/api/payments/webhook`)가 화면의 Vite 프록시를 통해 브라우저에 닿을 수 있으므로, 프록시는 `POST /api/payments`만 이 서비스로 보내고 `/api/payments/webhook`은 연결하지 않는다. 연결되어도 서명 검증이 막는다. | 기본 설정 시험, 프록시 설정 검토(u4·u5 연동 시) | NFR6, NFR8 |
| NFR6.8 | 요청 로그에는 세션 ID 앞 8자만 남기고 쿼리 문자열과 요청 본문은 기록하지 않는다. 오류 응답에 입력 원문을 되돌려 보내지 않는다. | 로그 출력 시험, 오류 응답 시험 | NFR6 |
| NFR8.1 | 다른 서비스 호출 주소(u2-reservation-service)와 모의 PG의 웹훅 주소는 환경 변수로 주입하며 코드에 하드코딩하지 않는다(기본값만 코드에 둔다). | 설정 검토 | NFR8 |

## 위협 고려

| 위협 | 대응 |
|---|---|
| 위조된 웹훅으로 결제 없이 예약 확정 | NFR6.1, NFR6.2. 비밀 값을 모르면 유효한 서명을 만들 수 없다 |
| 서명된 웹훅의 재생 | eventId 중복 기록(NFR5.1)과 u2의 멱등. occurredAt 최신성은 보지 않는다(프로토타입 한계) |
| 크기가 큰 본문으로 과부하 | NFR6.5 |
| 키 재사용으로 다른 주차권을 같은 결제로 위장 | 같은 (세션, 키)는 기존 결과를 돌려주며 값을 비교하지 않는다(프로토타입 한계, 금액은 u2가 확정) |
| 같은 머신의 다른 프로세스가 웹훅을 부름 | 서명 검증이 막는다. 비밀 값은 환경 변수로만 전달한다 |
| 세션 ID가 로그에 남음 | NFR6.8 |

## Assumptions & Open Questions

- [assumption] 웹훅 `occurredAt`의 최신성(오래된 서명 이벤트의 재생)은 검사하지 않는다. 근거: 인메모리 eventId 기록과 u2 멱등이 같은 이벤트의 재처리를 막고, 재시작 뒤에는 paymentId도 사라져 404가 된다.
- [assumption] 비밀 값의 최소 길이는 검사하지 않는다. 근거: 로컬 프로토타입이며 값은 u5-local-run이 주입한다.
- 열린 질문: 없음.

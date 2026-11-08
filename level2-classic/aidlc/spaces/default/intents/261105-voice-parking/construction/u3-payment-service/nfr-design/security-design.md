# u3-payment-service 보안 설계

## Sources

- [scope] Workflow-selected scope: classic
- [Q4] 로그인 없음, 익명 세션 (requirements-analysis Q4)
- [memory:M1] nfr-requirements/security-requirements.md(NFR6.1~NFR6.8, NFR8.1), rules.md(BR1.1, BR3.1~BR3.3), contract-summary.md(C2, C6)

| 설계 | 방식 | 충족하는 요구사항 |
|---|---|---|
| 서명 검증 | 본문 원문 바이트에 대해 `HmacSHA256`(키는 환경 변수 `PAYMENT_WEBHOOK_SECRET`의 UTF-8 바이트)을 계산해 소문자 hex로 만들고, 헤더 `X-Signature`가 정확히 `sha256=<64자 hex>` 형식일 때만 그 값을 `MessageDigest.isEqual`로 상수 시간 비교한다. 형식이 다르거나 헤더가 없으면 비교하지 않고 401이다. 서명 검증은 JSON 해석보다 앞에 한다. | NFR6.1 |
| 본문 한정 읽기 | 웹훅 경로 전용 서블릿 필터(`WebhookBodyFilter`)가 요청 본문을 직접 읽는다(컨트롤러의 `@RequestBody`로 읽지 않는다. 컨트롤러 전에 전체가 읽히는 것을 막기 위해서다). 이 필터는 요청 로그 필터보다 안쪽에서 실행되고 읽은 바이트를 요청 속성에 담아 서명 검증기와 컨트롤러가 쓴다. 요청 본문을 최대 8193바이트까지만 읽는다. 8192바이트를 넘으면 400 INVALID_REQUEST로 끝내고 더 읽지 않는다. 길이 헤더가 있으면 보조로 먼저 검사한다. 순서는 크기 검사(400) → 서명 검증(401) → 형식 검증(400) → 알려진 결제(404)다. | NFR6.5 |
| 비밀 값 | 시작할 때 환경 변수를 읽고 없거나 비어 있으면 기동을 거부한다. 소스와 설정에 키 문자열을 두지 않고, 빌드 단계에 흔한 비밀 값 패턴 검색 시험을 넣는다. 검색 범위는 운영 소스와 설정(`src/main`)이며 시험 소스는 제외한다(시험은 서명을 만들 시험용 값이 필요하다). 모의 PG는 같은 값을 주입받아 서명한다. 서명 계산 코드(`HmacSigner`)는 `payment`가 아닌 별도 `signature` 패키지에 두어 웹 계층의 서명 검증기와 모의 PG가 함께 쓰고, `pg`가 `web`을 참조하지 않게 한다. | NFR6.2 |
| 거부 기록 | 서명 거부는 시각, 본문 길이, 이유(`MISSING`, `MALFORMED`, `MISMATCH`)만 로그에 남긴다. 본문 내용과 서명 값은 남기지 않는다. 승인 무시, 중복도 결제 ID, 예약 ID, eventId, outcome과 함께 남긴다. | NFR6.3 |
| 경계 입력 검증 | 컨트롤러에서 결제 요청(세션 UUID, passId·idempotencyKey 1~100자, durationHours 1~24, mockScenario 허용값, mockDelaySeconds 0~600)과 웹훅 본문(eventId 1~100자, paymentId, outcome 허용값, occurredAt 날짜 시각)을 검증한다. 결제 요청의 세션 오류는 400 MISSING_SESSION, 그 밖의 형식 오류는 400 INVALID_REQUEST다. 웹훅은 세션이 없다. 검증은 서비스 로직보다 앞에서 한다. | NFR6.4 |
| 인증 없음 | 인증이 없다. 세션 ID는 식별용이며 이 서비스는 세션별 조회를 제공하지 않는다. 인증을 우회하는 코드를 만들지 않는다. | NFR6.6 |
| 루프백 바인딩과 웹훅 경로 | 서비스를 루프백 주소에만 연다(`server.address=127.0.0.1`, 환경 변수로 바꿀 수 있고 기본값이 루프백). 웹훅 경로는 브라우저가 프록시를 통해 닿을 수 있으므로 방어선은 서명 검증 하나다. 이 잔여 위험은 받아들이고, 아래 "다른 유닛에 넘기는 의무"로 프록시 노출을 줄인다. | NFR6.7 |
| 로그 최소화 | 요청 로그에는 세션 ID 앞 8자만 남기고 쿼리 문자열과 요청 본문은 기록하지 않는다. 오류 본문에는 고정 문구와 오류 코드만 넣고 입력 원문을 되돌려 보내지 않는다. | NFR6.8 |
| 주소는 환경 변수 | u2 주소는 `RESERVATION_SERVICE_URL`, 모의 PG의 웹훅 주소는 `WEBHOOK_URL`, 포트는 `SERVER_PORT`로 주입한다. 기본값만 코드에 둔다. | NFR8.1 |

## 대안

- 웹훅을 별도 포트(내부 전용)에 여는 안: 프록시 노출은 막지만 포트가 하나 늘고 u5-local-run과 모의 PG 설정이 복잡해진다. 서명 검증이 이미 막고 있어 선택하지 않았다.
- 시각 기반 재생 방지(`occurredAt` 최신성 검사): 인메모리 eventId 기록과 u2 멱등이 같은 이벤트의 재처리를 막아 프로토타입에서는 선택하지 않았다.

## 보안 헤더, 암호화

별도 보안 헤더와 암호화는 적용하지 않는다. 로컬 환경에서만 동작하고 실제 결제 정보를 다루지 않는다.

## 다른 유닛에 넘기는 의무

- u4-web-ui, u5-local-run (필수): Vite 프록시 설정에서 `/api/payments`를 접두사로 연결하면 `/api/payments/webhook`도 브라우저에 열린다. `POST /api/payments`만 정확 일치로 이 서비스에 연결하고(예: 경로 끝을 고정하는 정규식 키) `/api/payments/webhook`은 연결하지 않는다(NFR6.7). 브라우저 경유 웹훅 호출이 이 서비스에 닿지 않는지(404) 연동 시험 항목에 넣는다. 공유 계약(contract-summary.md)은 `/api/payments`를 접두사로 적고 있어 검토가 끝난 문서라 고치지 않으며, 이 의무는 이 서비스의 `code-summary.md`로 u4·u5에 전한다. 연결이 정확 일치로 되어 있지 않아 웹훅 경로가 열려도 서명 검증이 막으므로 서명 검증은 마지막 방어선이다.
- u5-local-run: 이 서비스를 기본 설정(루프백)으로 띄우고 `PAYMENT_WEBHOOK_SECRET`을 주입한다. 이 값은 이 서비스 한 곳에만 둔다.

## Assumptions & Open Questions

- [assumption] 서명된 이벤트의 시각 기반 재생(오래된 `occurredAt`)과 비밀 값 최소 길이는 검사하지 않는다. 근거: 로컬 프로토타입이며 인메모리 eventId 기록이 중복을 막는다.
- [assumption] 웹훅 경로의 프록시 노출은 서명 검증 하나로 막는 잔여 위험이다. 사람이 확인할 항목이다.

# u4-web-ui 화면 규칙

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] functional-spec.md(W1~W5, UR1.1~UR8.1), requirements.md(FR1~FR8, NFR1), stories.md(US1.1~US4.2), refined-mockups/interaction-spec.md, accessibility-checklist.md, components.md(domain-design의 컴포넌트 5개는 모두 서비스 쪽이다. 이 유닛은 화면 컴포넌트이며 components.md의 컴포넌트를 소유하지 않는다)

이 파일은 `functional-spec.md`의 화면 규칙 `UR{n}.{m}`을 추적 도구가 읽는 형식(`BR{n}.{m}`)으로 옮긴 것이다. 번호가 같다: `BR2.3`은 `UR2.3`이다. 기능 명세가 규칙의 서술 원본이고 이 파일은 같은 규칙의 짧은 요약이다. 화면 규칙은 서비스가 소유하는 비즈니스 규칙(점수 계산, 상태 확정 등)을 다시 정하지 않고 화면이 무엇을 보내고 어떻게 보여 주는지만 정한다.

```yaml
rules:
  - { id: BR1.1, statement: "세션 ID는 sessionStorage의 UUID이며 없으면 만든다. 새로 고침 뒤에도 같은 탭에서는 같다.", category: policy, applies_to: 세션, trigger: "화면을 처음 열 때", logic: "IF sessionStorage에 세션 UUID가 없으면 THEN crypto.randomUUID()로 만들어 저장한다.", violation: "해당 없음", source: [FR8.1] }
  - { id: BR1.2, statement: "모든 서비스 요청에 X-Session-Id 헤더를 붙인다. SSE는 쿼리 sessionId로 보낸다.", category: constraint, applies_to: API 호출, trigger: "요청을 보낼 때", logic: "fetch는 헤더, EventSource는 쿼리 sessionId를 쓴다.", violation: "해당 없음", source: [FR8.1] }
  - { id: BR1.3, statement: "처음 열면 세션의 현재 예약을 조회해 상태를 복원한다.", category: policy, applies_to: 예약 상태 영역, trigger: "화면을 처음 열거나 새로 고칠 때", logic: "GET /api/reservations/current가 404면 S1, 예약이 있으면 상태에 맞는 S5~S8을 보이고 PENDING_PAYMENT면 구독을 시작한다.", violation: "해당 없음", source: [FR6.3] }
  - { id: BR1.4, statement: "저장된 마지막 추천 목록이 있으면 S3으로 복원한다.", category: policy, applies_to: 결과 영역, trigger: "화면을 처음 열 때", logic: "sessionStorage에 추천 목록이 있고 읽을 수 있으면 보인다. 저장소를 쓸 수 없으면 복원 없이 동작한다.", violation: "해당 없음", source: [FR3.4] }
  - { id: BR2.1, statement: "입력은 앞뒤 공백 제거 후 1~200자이며 비면 전송 버튼이 비활성이다.", category: validation, applies_to: 입력창, trigger: "입력할 때", logic: "공백만이거나 비면 비활성, 200자 초과는 maxlength로 막는다.", violation: "전송 버튼 비활성", source: [FR1.1] }
  - { id: BR2.2, statement: "전송하면 추천을 요청하고 응답 전까지 버튼 비활성과 '찾는 중...'을 보인다.", category: policy, applies_to: 추천 요청, trigger: "전송 버튼 또는 엔터", logic: "POST /api/search/recommendations { text }. 요청 중에는 중복 전송을 막는다.", violation: "해당 없음", source: [FR1.1, FR1.2] }
  - { id: BR2.3, statement: "추천 카드는 서버가 준 순서와 값을 그대로 보인다. 최적 1개와 비교 후보를 표시한다.", category: policy, applies_to: 추천 카드, trigger: "추천 응답 200", logic: "이름, 요금(이용 시간 합계), 거리, 종합 점수, 이유 문구를 보인다. 화면은 점수를 계산하거나 순서를 바꾸지 않는다.", violation: "해당 없음", source: [FR3.4, FR3.5] }
  - { id: BR2.4, statement: "후보가 없으면 안내를 보인다.", category: policy, applies_to: 결과 영역, trigger: "recommendations가 빈 배열", logic: "S4 안내 문구를 보인다.", violation: "해당 없음", source: [FR2.3] }
  - { id: BR2.5, statement: "해석 오류(422)는 부족한 항목을 안내하고 추천 목록을 비운다.", category: policy, applies_to: 결과 영역, trigger: "422 MISSING_PLACE 또는 MISSING_DURATION", logic: "S2 안내를 보이고 이전 추천은 지운다. 추천은 수행되지 않았다.", violation: "해당 없음", source: [FR1.2] }
  - { id: BR2.6, statement: "그 밖의 추천 오류는 다시 시도 안내를 보이고 이전 결과는 유지한다. 제한 시간은 3초다.", category: policy, applies_to: 오류 처리, trigger: "네트워크 오류, 400, 5xx, 3초 초과", logic: "'잠시 후 다시 시도해 주세요'를 보이고 다시 전송할 수 있다.", violation: "해당 없음", source: [NFR3] }
  - { id: BR3.1, statement: "'이 주차권 결제'는 확인 창 없이 바로 결제를 요청한다.", category: policy, applies_to: 결제 요청, trigger: "카드 버튼 클릭", logic: "POST /api/payments { passId, durationHours(query.durationHours), idempotencyKey, mockScenario, mockDelaySeconds }.", violation: "해당 없음", source: [FR4.1, FR4.2] }
  - { id: BR3.2, statement: "멱등 키는 '마지막 미결 시도'(passId, key)로 정한다. 같은 후보를 다시 누르면 같은 키, 확정된 응답을 받거나 다른 후보를 누르면 새 키.", category: constraint, applies_to: 결제 요청, trigger: "결제 요청과 재시도", logic: "보관된 시도가 없거나 passId가 다르면 새 UUID를 보관한다. 응답을 못 받은 경우(네트워크, 3초, 502, 5xx)는 지우지 않아 같은 후보 재시도가 같은 키를 쓴다. 202·200·409·404·422를 받거나 새로 시작하면 지운다.", violation: "해당 없음", source: [FR4.2] }
  - { id: BR3.3, statement: "202면 결제 대기 상태로 바꾸고 해당 예약의 구독을 시작한다.", category: policy, applies_to: 예약 상태 영역, trigger: "결제 응답 202", logic: "S5를 보이고 reservationId로 SSE를 구독한다.", violation: "해당 없음", source: [FR4.3, FR6.1] }
  - { id: BR3.4, statement: "결제 대기 중에는 모든 결제 버튼이 비활성이고 새 검색은 허용한다.", category: constraint, applies_to: 결제 버튼, trigger: "예약 상태가 PENDING_PAYMENT인 동안", logic: "결제 버튼 비활성. 새 검색은 가능하며 결과가 와도 버튼은 비활성이다.", violation: "해당 없음", source: [FR4.2] }
  - { id: BR3.5, statement: "결제 응답 200은 status로 갈린다. 대기 중이면 202처럼 S5와 구독, 종료 상태면 단건 조회로 값을 채워 S6~S8.", category: policy, applies_to: 예약 상태 영역, trigger: "결제 응답 200", logic: "PENDING_PAYMENT는 S5 + 구독. 종료 상태는 C2 응답에 주차장·이용 시간·금액이 없으므로 GET /api/reservations/{id}로 채운다. 조회가 실패하면 새로 고침 안내.", violation: "해당 없음", source: [FR4.2] }
  - { id: BR3.6, statement: "결제 접수 오류는 사유별 안내를 보이고 재시도를 허용한다.", category: policy, applies_to: 오류 처리, trigger: "결제 응답 오류", logic: "409는 기존 예약의 S5, 404·422는 사용 불가 안내, 502·시간 초과·그 밖은 접수 오류 안내와 같은 키 재시도.", violation: "해당 없음", source: [FR4.2] }
  - { id: BR3.7, statement: "결제 실패 뒤 같은 목록의 다른 후보를 새 키로 결제할 수 있고 실패한 카드는 '결제 실패'로 막는다.", category: policy, applies_to: 추천 카드, trigger: "S7에서 다른 후보 클릭", logic: "새 키로 BR3.1을 다시 한다. 마지막으로 결제를 누른 passId의 카드는 비활성이다.", violation: "해당 없음", source: [FR5.5] }
  - { id: BR4.1, statement: "S5가 되면 예약 한 건의 SSE를 구독한다.", category: policy, applies_to: 실시간 갱신, trigger: "S5 진입", logic: "EventSource /api/reservations/{id}/events?sessionId=.", violation: "해당 없음", source: [FR6.1] }
  - { id: BR4.2, statement: "스냅샷을 받으면 즉시 화면 상태를 바꾸되 같은 예약에서 version이 낮거나 같으면 버린다(예약별 필터, 새 구독 때 초기화, 구독·/current·단건 조회 공통).", category: policy, applies_to: 예약 상태 영역, trigger: "snapshot 이벤트", logic: "CONFIRMED는 S6(주차장, 이용 시간, 결제 금액), PAYMENT_FAILED는 S7, CANCELLED는 S8, PENDING_PAYMENT는 S5 유지. 지연시키지 않는다.", violation: "해당 없음", source: [FR6.2, NFR1] }
  - { id: BR4.3, statement: "종료 상태를 받으면 구독을 닫는다.", category: policy, applies_to: 구독, trigger: "CONFIRMED, PAYMENT_FAILED, CANCELLED 스냅샷", logic: "서버는 연결을 닫지 않으므로 화면이 EventSource를 닫는다.", violation: "해당 없음", source: [FR6.1] }
  - { id: BR4.4, statement: "연결이 끊기면 재연결하고, 닫힌 연결은 단건 조회와 재구독으로 복구한다. 물러나기와 총 60초 한도가 있다.", category: policy, applies_to: 구독, trigger: "연결 오류", logic: "브라우저 자동 재연결 시 서버가 최신 스냅샷을 먼저 보낸다. CLOSED면 1·2·5초 물러나기로 GET /api/reservations/{id}로 반영하고 다시 구독한다. 404는 S1로 가며 중단, 401은 세션 안내 후 중단, 종료 상태면 중단, 60초 안에 복구되지 않으면 새로 고침 안내 후 중단, S1로 돌아가면 중단. 예약당 구독은 하나이며 새 구독 전에 기존 구독을 닫는다.", violation: "해당 없음", source: [FR6.3] }
  - { id: BR4.5, statement: "경과 시간은 1초마다 갱신하되 화면은 스스로 취소하지 않는다.", category: policy, applies_to: 결제 대기 화면, trigger: "S5에서 1초마다", logic: "paymentRequestedAt부터 지금까지 0~180초를 보인다. 취소는 서버가 확정해 푸시한다.", violation: "해당 없음", source: [FR7.1, FR7.2] }
  - { id: BR4.6, statement: "상태 변화를 aria-live 영역에 알린다.", category: constraint, applies_to: 접근성, trigger: "예약 상태 변경", logic: "aria-live=polite 영역에 안내 문구를 넣는다.", violation: "해당 없음", source: [FR6.2] }
  - { id: BR5.1, statement: "새로 시작하면 두 영역과 구독과 저장된 추천을 비우고 세션 ID는 유지한다.", category: policy, applies_to: 새로 시작, trigger: "'새로 검색하기', '다시 검색하기'", logic: "S1로 간다.", violation: "해당 없음", source: [FR8.1] }
  - { id: BR6.1, statement: "개발용 시연 설정으로 모의 결제 결과와 지연 초를 고른다.", category: policy, applies_to: 결제 요청, trigger: "시연 설정 변경", logic: "승인(기본), 지연(1~600초, 기본 200), 실패를 mockScenario·mockDelaySeconds로 보낸다. 승인·실패에서는 지연 초 0. 설정은 새로 고침하면 기본값으로 돌아간다.", violation: "범위 밖 값은 한계로 맞춤", source: [FR5.5] }
  - { id: BR7.1, statement: "기본 접근성(A1~A6)을 지킨다.", category: constraint, applies_to: 모든 화면, trigger: "항상", logic: "키보드 조작, 포커스 윤곽선, 대비 4.5:1, 문구와 (!) 병기, 입력창 레이블, aria-live.", violation: "해당 없음", source: [NFR9] }
  - { id: BR8.1, statement: "서비스 연결은 프록시이며 SSE가 버퍼링 없이 전달되는지 시험한다.", category: constraint, applies_to: 프록시, trigger: "개발 서버 설정", logic: "/api/search→u1, /api/reservations→u2, POST /api/payments(정확 일치)→u3. 웹훅 경로는 연결하지 않는다.", violation: "해당 없음", source: [FR6.1, NFR1] }
```

## 요약

| 규칙 | 한 줄 요약 |
|---|---|
| BR1.1~BR1.4 | 세션 ID, 요청 헤더, 현재 예약 복원, 추천 목록 복원 |
| BR2.1~BR2.6 | 입력 검증, 추천 요청, 카드 표시, 후보 없음, 해석 오류, 요청 오류 |
| BR3.1~BR3.7 | 결제 요청, 멱등 키, 접수, 결제 대기 중 제한, 종료된 예약 재요청, 접수 오류, 실패 뒤 재결제 |
| BR4.1~BR4.6 | 구독, 스냅샷 반영과 version 필터, 종료 시 닫기, 연결 복구, 경과 시간, aria-live |
| BR5.1, BR6.1, BR7.1, BR8.1 | 새로 시작, 시연 설정, 접근성, 프록시 |

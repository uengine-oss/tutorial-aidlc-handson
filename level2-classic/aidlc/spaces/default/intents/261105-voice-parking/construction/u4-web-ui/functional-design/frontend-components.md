# u4-web-ui 프런트엔드 컴포넌트

## Sources

- [scope] Workflow-selected scope: classic
- [Q2] UI 라이브러리 없음, 직접 작성한 CSS (refined-mockups Q2)
- [memory:M1] functional-spec.md(W1~W5, UR1.1~UR8.1), refined-mockups/design-system-mapping.md(컴포넌트 매핑과 디자인 토큰), accessibility-checklist.md, contract-summary.md(C1, C2, C3), team.md(Vue 3, ESLint + Prettier)

Vue 3 단일 파일 컴포넌트(Composition API, `<script setup>`), 번들러는 Vite. 라우터와 상태 관리 라이브러리는 쓰지 않는다. 상태는 한 composable(`useApp`)이 소유하고 컴포넌트는 props와 이벤트로만 소통한다. 파일 이름은 PascalCase, 변수는 camelCase다.

## 컴포넌트 계층

```
App.vue                      레이아웃, useApp 연결, aria-live 영역
├─ QueryInput                입력창과 전송 (S1, S2)
│   └─ ErrorNotice           해석 오류·요청 오류 안내
├─ ErrorNotice               결제 접수 오류·주차권 사용 불가 안내 (결과 영역 위)
├─ ReservationStatus         결제 대기·완료·실패·취소 (S5~S8)
├─ RecommendationList        추천 카드 묶음 (S3, S7의 카드 목록)
│   ├─ ParkingCard (best)    최적 추천
│   └─ ParkingCard (candidate) × 최대 2   비교 후보
├─ EmptyNotice               후보 없음 (S4)
└─ ScenarioPanel             접힌 시연 설정 (모의 결제 결과, 지연 초)
```

디자인 시스템 매핑 문서의 컴포넌트 5개(QueryInput, ErrorNotice, ParkingCard, ReservationStatus, EmptyNotice)에 `RecommendationList`와 `ScenarioPanel`을 더했다. `RecommendationList`는 카드 배치를, `ScenarioPanel`은 계약의 열린 질문(mockScenario 노출)을 맡는다.

## 컴포넌트 명세

| 컴포넌트 | props | 이벤트 | 설명 |
|---|---|---|---|
| QueryInput | `modelValue`(string), `disabled`(boolean, 요청 중), `error`(string, 선택), `loading`(boolean) | `update:modelValue`, `submit` | 한 줄 입력(최대 200자), 레이블 연결(A5), 엔터와 버튼이 같은 동작, 공백만이면 버튼 비활성, 예시 문구, `loading`이면 "찾는 중..." |
| ErrorNotice | `message`(string) | 없음 | `(!)` 문자와 문구. `role="alert"`는 쓰지 않고, 같은 문구를 App의 aria-live 영역에 알린다(알릴 사건 목록은 기능 명세 UR4.6) |
| ParkingCard | `rank`('BEST'\|'CANDIDATE'), `lotName`, `price`(number), `durationHours`(number), `distanceMeters`(number), `score`(number), `reasonText`(string), `disabled`(boolean), `failed`(boolean) | `select` | 카드 표시(UR2.3 표), `failed`이면 "결제 실패" 표시와 버튼 비활성, `disabled`이면 버튼 비활성. `variant`는 `rank`에서 정한다(디자인 시스템 문서의 variant) |
| RecommendationList | `items`(Recommendation[]), `durationHours`, `payDisabled`(boolean), `failedPassId`(string\|null) | `select`(passId) | BEST를 먼저, CANDIDATE를 이어서 보인다. 서버가 준 순서를 바꾸지 않는다 |
| ReservationStatus | `state`('pending'\|'confirmed'\|'failed'\|'cancelled'), `message`(string), `lotName`, `durationHours`, `amount`, `elapsedSeconds`(number), `waitingConfirm`(boolean, 경과 180초 도달), `hasCards`(boolean) | `restart` | 상태별 표시(S5~S8). pending이면 경과 시간(`waitingConfirm`이면 "승인 여부를 확인하는 중입니다..."), confirmed이면 주차장·이용 시간·결제 금액과 "새로 검색하기", cancelled이면 "다시 검색하기", failed이면 재선택 안내 문구(`hasCards`가 false이면 "새로 검색하기"도 보인다). 사용자 동작으로 상태가 바뀌었을 때만 제목에 포커스를 옮긴다. 경과 시간은 aria-live 영역 밖에 둔다 |
| EmptyNotice | `message`(string) | 없음 | 후보 없음 안내 |
| ScenarioPanel | `scenario`('APPROVE'\|'DELAY'\|'FAIL'), `delaySeconds`(number) | `update:scenario`, `update:delaySeconds` | `<details>` 접힌 영역, 지연일 때만 지연 초 입력(0~600) |

## 상태와 composable

| 단위 | 책임 |
|---|---|
| `useSession` | sessionStorage에서 세션 UUID를 읽거나 만들고(UR1.1), 마지막 추천 목록과 마지막 결제 passId를 저장·복원한다(저장소 오류는 삼키고 복원 없이 계속) |
| `api` (모듈) | 상대 경로 fetch 래퍼. `X-Session-Id` 헤더, 3초 제한 시간(`AbortController`), 오류 본문 `{ code, message }` 파싱. 함수: `recommend(text)`, `pay(request)`, `currentReservation()`, `reservation(id)` |
| `useReservationStream` | EventSource 구독·해제(예약당 하나, 새 구독 전에 기존을 닫음), 예약별 `version` 필터(구독·`/current`·단건 조회 공통), 종료 상태에서 닫기, 닫힌 연결 복구(1·2·5초 물러나기, 총 60초, 404는 S1·401은 안내 후 중단)(UR4.x) |
| `useApp` | 화면 상태(두 영역의 곱), 입력 텍스트, 추천 결과, 결제 요청 중 여부, 안내 문구, 경과 시간(주입 가능한 `now()`로 1초 틱), 보관된 미결 결제 시도 `{ passId, key }`와 `lastPaidPassId`(UR3.2), 동작: `submitQuery`, `pay(passId)`, `restart`, `init` |

화면 상태 값(`resultView`: `idle`·`interpretError`·`results`·`empty`, `reservationView`: `none`·`pending`·`confirmed`·`failed`·`cancelled`)은 `useApp`이 정한다. 컴포넌트는 판단하지 않고 보여 주기만 한다.

## 입력 검증 (UR2.1)

| 필드 | 규칙 | 위반 시 |
|---|---|---|
| 입력 텍스트 | 앞뒤 공백을 제거한 길이 1~200자 | 전송 버튼 비활성(오류 문구 없음), 200자를 넘는 입력은 입력창 `maxlength`로 막는다 |
| 지연 초 (시연 설정) | 정수 1~600 (지연 시나리오에서만, 기본 200). 승인·실패에서는 0으로 보낸다 | 범위 밖 값은 가장 가까운 한계로 맞춘다 |

서버가 최종 검증하며(예: 장소·시간 해석) 화면은 서버 오류 코드를 문구로 바꿔 보인다.

## API 연결 지점

| 동작 | 호출 | 성공 처리 | 오류 처리 |
|---|---|---|---|
| 추천 | `POST /api/search/recommendations` `{ text }` | 200 → S3/S4. 응답의 `query.durationHours`를 요금 표시에 쓴다 | 422 MISSING_PLACE·MISSING_DURATION → S2, 그 밖 → "잠시 후 다시 시도해 주세요" |
| 결제 | `POST /api/payments` `{ passId, durationHours, idempotencyKey, mockScenario, mockDelaySeconds }` | 202 → S5 + SSE. 200은 `status`로 갈린다: PENDING_PAYMENT → 202와 같이 S5 + SSE, 종료 상태 → 응답의 `reservationId`로 `GET /api/reservations/{id}`를 불러 S6~S8을 채움 | 409 → 기존 예약으로 S5, 404·422 → 사용 불가 안내(미결 시도 삭제), 502·시간 초과·그 밖 → 접수 오류 안내(미결 시도 유지, 같은 후보를 다시 누르면 같은 키) |
| 현재 예약 | `GET /api/reservations/current` | 상태에 따라 S5~S8, S5이면 SSE | 404 NO_RESERVATION → S1, 그 밖 → S1 (조용히 시작) |
| 예약 한 건 | `GET /api/reservations/{id}` | 상태 반영(같은 version 필터) | 404 → 예약 상태 영역을 비우고 S1로 가며 복구를 멈춤. 401 → 세션 안내 후 복구 중단 |
| 구독 | `EventSource GET /api/reservations/{id}/events?sessionId=` | `snapshot` 이벤트 반영(예약별 version 필터). 예약당 구독은 하나이며 새 구독 전에 기존 구독을 닫는다 | 연결 오류 → 자동 재연결, 닫히면 1·2·5초 물러나기로 총 60초 복구 후 안내(UR4.4) |

## 상호작용 흐름 (키보드와 포커스)

- 처음 열면(복원 포함) 입력창에 포커스가 있다.
- 추천 결과가 오면 포커스는 입력창에 둔다(스크린 리더는 aria-live로 "추천 N건"을 듣는다). 카드 버튼은 Tab 순서대로 이동한다.
- 사용자 동작으로 예약 상태가 바뀌면(결제 응답, 구독 푸시) `ReservationStatus`의 제목(`tabindex="-1"`)으로 포커스를 옮기고, 복원으로 보이는 상태에서는 포커스를 옮기지 않는다. 어느 경우에도 안내 문구를 aria-live 영역에 알린다.
- "새로 검색하기"를 누르면 입력창으로 포커스가 돌아온다.

## 디자인 토큰 (CSS 변수)

디자인 시스템 매핑의 이름을 그대로 쓴다: `--color-text`, `--color-bg`, `--color-primary`, `--color-success`, `--color-danger`, `--space-1`~`--space-4`, `--font-body`, `--font-title`. 값은 구현 단계에서 정하되 본문 대비는 4.5:1 이상이고 오류·실패·취소는 색뿐 아니라 `(!)` 문자와 문구로도 표시한다.

## data-testid

시험 자동화를 위해 상호작용 요소에 `data-testid`를 둔다: `query-input`, `query-submit`, `query-error`, `card-{passId}`, `pay-{passId}`, `status-pending`, `status-confirmed`, `status-failed`, `status-cancelled`, `status-heading`, `restart`, `empty-notice`, `pay-error`, `scenario-select`, `delay-input`, `live-region`.

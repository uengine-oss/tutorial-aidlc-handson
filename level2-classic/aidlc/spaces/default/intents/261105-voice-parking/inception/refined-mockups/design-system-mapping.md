# Design System Mapping

## Sources

- [scope] Workflow-selected scope: classic
- [Q2] UI 컴포넌트 라이브러리 없음, 직접 작성한 CSS
- [memory:M1] team-practices.md: 프런트 Vue 3, ESLint + Prettier

## 접근 방식

외부 디자인 시스템이나 UI 라이브러리를 쓰지 않는다. 아래 컴포넌트를 Vue 3 단일 파일 컴포넌트로 직접 만든다.

## 컴포넌트 매핑

| 컴포넌트 | 사용 화면 | 속성(props) | 이벤트 |
|---|---|---|---|
| QueryInput | S1, S2 | value, error | submit |
| ErrorNotice | S2, S7, S8 | message | 없음 |
| ParkingCard | S3, S7 | name, price, distance, score, reason, variant (best 또는 candidate), disabled | select |
| ReservationStatus | S5, S6, S8 | state (pending, confirmed, failed, cancelled), summary, elapsedSeconds | restart |
| EmptyNotice | S4 | message | 없음 |

## 디자인 토큰

색, 글자 크기, 간격은 CSS 변수로 한곳에 둔다. 값은 구현 단계에서 확정하고 여기서는 이름과 용도만 정한다.

| 토큰 | 용도 |
|---|---|
| --color-text, --color-bg | 본문 글자와 배경 |
| --color-primary | 주요 버튼, 최적 추천 카드 강조 |
| --color-success | 예약 완료 |
| --color-danger | 결제 실패, 자동 취소, 입력 오류 |
| --space-1 ~ --space-4 | 간격 4단계 |
| --font-body, --font-title | 글자 크기 2단계 |

## 코딩 규칙

- 포매터와 린터는 ESLint와 Prettier를 따른다.
- 컴포넌트 파일은 PascalCase, 변수는 camelCase.

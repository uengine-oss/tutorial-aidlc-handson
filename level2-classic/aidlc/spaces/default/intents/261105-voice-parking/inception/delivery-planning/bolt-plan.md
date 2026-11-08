# Bolt Plan

## Sources

- [desc] Initial description: 사용자가 음성 텍스트('강남역 근처 2시간 주차권 찾아줘')를 보내면 최적 주차권을 추천하고 결제와 예약을 처리하는 VoiceParking 프로토타입을 만들어 줘.
- [scope] Workflow-selected scope: classic
- [Q1] Bolt는 유닛당 하나, 총 5개
- [Q2] 가장 걱정되는 부분은 승인 후 1초 안 화면 표시
- [memory:M1] requirements.md, stories.md, mockups.md, components.md, unit-of-work.md, unit-of-work-dependency.md, unit-of-work-story-map.md, contract-summary.md, team-practices.md (얇은 end-to-end 조각을 먼저 만들지 않음, 서비스별로 완성, Bolt는 한 번에 하나씩 순서대로, Docker 없음)

Bolt는 작업 일부를 한 번 만들고 실행되는 결과로 끝내는 한 판이다. 이 계획의 Bolt는 유닛(독립 구현 단위) 하나에 대응한다. 순서는 유닛 사이 의존(사슬)이 정하므로 아래 순서에서 벗어나는 곳은 없다. 팀 규칙에 따라 워킹 스켈레톤(전 흐름을 끝까지 관통하는 최소 조각을 먼저 만드는 방식)은 쓰지 않는다.

## Bolt 순서

| Bolt | 유닛 | 워킹 스켈레톤 | 만드는 순서의 이유 |
|---|---|---|---|
| B1 | U1 u1-search-service | 아님 | 의존 대상이 없어 가장 먼저 만들 수 있다 |
| B2 | U2 u2-reservation-service | 아님 | U1에 의존 |
| B3 | U3 u3-payment-service | 아님 | U2에 의존 |
| B4 | U4 u4-web-ui | 아님 | U1~U3에 의존 |
| B5 | U5 u5-local-run | 아님 | U1~U4에 의존 |

## B1 u1-search-service

- Definition of Done: AC1.1.1~AC1.1.3, AC2.1.1~AC2.1.3, AC2.2.1~AC2.2.5(점수 계산의 숫자 예시 포함)가 자동 시험으로 통과하고, C1과 C4 계약 시험이 통과한다. 80% 라인 커버리지.
- Confidence hypothesis: 종합 점수(가격 0.4, 거리 0.4, 시간 적합도 0.2)가 min-max 환산과 동점 규칙까지 포함해 기대한 순위를 준다.
- Expected demo: 음성 텍스트 요청에 최적 1개와 비교 후보 2개가 이유와 함께 돌아온다.

## B2 u2-reservation-service

- Definition of Done: AC3.1.3, AC4.1.1(서버 측 시간 측정), AC4.1.4, AC4.1.5, AC4.2.1~AC4.2.4가 시험으로 통과하고, C3, C5 계약 시험과 `applyPaymentResult` 경합 시험이 통과한다. 80% 라인 커버리지.
- Confidence hypothesis: 결제 결과 반영과 180초 자동 취소가 같은 순간 겹쳐도 하나만 확정되고, 승인 후 서버 안에서 SSE 푸시가 150ms 안에 나간다. 걱정거리(승인 후 1초 안 화면 표시)의 서버 쪽 몫을 가장 먼저 확인하는 판이다.
- Expected demo: 시험 시계로 승인 직후 SSE 이벤트와 180초 자동 취소 이벤트를 보여 준다.

## B3 u3-payment-service

- Definition of Done: AC3.1.1, AC3.1.2, AC3.2.1~AC3.2.3, AC4.1.2, AC4.1.3이 시험으로 통과하고, C2, C6 계약 시험과 서명 검증(HMAC) 시험이 통과한다. 80% 라인 커버리지.
- Confidence hypothesis: 모의 PG가 자기 웹훅 엔드포인트를 HTTP로 비동기 호출하는 경로가 실제 PG와 같은 서명 검증을 거치며, 중복·위조·취소 뒤 승인이 모두 안전하게 처리된다.
- Expected demo: 승인, 지연, 실패 시나리오를 각각 실행해 U2의 예약 상태가 기대대로 바뀐다.

## B4 u4-web-ui

- Definition of Done: 화면 S1~S8이 동작하고 AC1.1.2~AC1.1.4, AC2.2.2, AC3.1.2, AC3.2.2, AC4.1.1(웹훅 수신부터 화면 표시까지 100건 반복 최대 1초)이 시험으로 통과한다. Vite 프록시가 SSE를 버퍼링하지 않는지 확인하는 시험이 있다. 기본 접근성 체크리스트 A1~A6이 통과한다. 80% 라인 커버리지.
- Confidence hypothesis: Vite 프록시를 거쳐도 SSE가 지연 없이 전달되어 웹훅 수신부터 화면 표시까지 1초 이내다. 걱정거리의 마지막 구간을 확인하는 판이다.
- Expected demo: 브라우저에서 입력 → 추천 → 결제 → 예약 완료까지 실제로 본다.

## B5 u5-local-run

- Definition of Done: AC5.1.1~AC5.1.3. 실행 스크립트 한 번으로 U1~U4가 기동되고 기동 실패 서비스를 오류로 알린다. Docker는 쓰지 않는다. 웹훅 비밀 값은 환경 변수로 주입된다.
- Confidence hypothesis: 별도 배포 환경 없이 로컬 스크립트만으로 전체 흐름을 재현할 수 있다.
- Expected demo: 스크립트를 실행한 뒤 "강남역 근처 2시간 주차권 찾아줘"로 예약 완료까지 간다.

## 통합 확인 방식

- Construction 검증 명령은 아직 정하지 않았다. 새 프로젝트라 실행할 수 있는 검사가 아직 없어서, 첫 Bolt를 마칠 때 사람에게 묻는다. 이 명령은 모든 시험 단계(단위, 계약, 통합)와 커버리지 검사를 실행해야 한다.

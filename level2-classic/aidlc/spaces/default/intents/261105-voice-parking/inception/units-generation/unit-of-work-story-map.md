# Unit Story Map

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] stories.md의 스토리 US1.1~US5.1, unit-of-work.md의 유닛 U1~U5

## 스토리와 유닛

| Story | Unit | Directory |
|---|---|---|
| US1.1 | U1; U4 | u1-search-service; u4-web-ui |
| US2.1 | U1; U4 | u1-search-service; u4-web-ui |
| US2.2 | U1; U4 | u1-search-service; u4-web-ui |
| US3.1 | U2; U3; U4 | u2-reservation-service; u3-payment-service; u4-web-ui |
| US3.2 | U2; U3; U4 | u2-reservation-service; u3-payment-service; u4-web-ui |
| US4.1 | U2; U3; U4 | u2-reservation-service; u3-payment-service; u4-web-ui |
| US4.2 | U2; U4 | u2-reservation-service; u4-web-ui |
| US5.1 | U5 | u5-local-run |

## 여러 유닛에 걸치는 항목

- 모든 화면 스토리(US1.1~US4.2)는 서비스 유닛과 U4 화면 유닛이 함께 구현한다. 화면은 표시와 입력, 서비스는 규칙과 상태를 맡는다.
- US4.1(예약 완료 1초 이내 표시)은 U3(웹훅 수신), U2(상태 확정과 푸시), U4(수신과 표시)가 모두 시간 예산을 나눠 쓴다.

## 유닛 안의 구현 순서

| Unit | 순서 |
|---|---|
| U1 | 입력 해석, 후보 조회와 제외, 점수 계산과 순위, 추천 이유, 주차권 조회 |
| U2 | 예약 상태 전이와 원자 연산, 180초 자동 취소, 세션 조회, SSE 푸시와 재구독 |
| U3 | 결제 요청과 대기 예약 생성, 모의 PG 시나리오, 웹훅 검증과 멱등 처리, 결과 반영 |
| U4 | 입력과 추천 화면, 결제와 대기 화면, 실시간 구독과 완료·실패·취소 화면 |
| U5 | 서비스별 기동 명령, 환경 변수와 포트, 전체 기동과 실패 알림 |

## 커버리지 확인

- 스토리 US1.1~US5.1은 모두 한 개 이상의 유닛에 배정되었다.
- 유닛 U1~U5는 모두 한 개 이상의 스토리 행을 가진다.

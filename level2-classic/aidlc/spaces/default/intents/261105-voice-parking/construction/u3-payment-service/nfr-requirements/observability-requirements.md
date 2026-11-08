# u3-payment-service 관측성 요구사항

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] requirements.md(NFR1, NFR5, NFR6), rules.md의 BR3.1, BR3.5, BR3.6, phases/operation.md(새 서비스마다 상태 지표와 오류율 지표 최소 하나), team.md(별도 배포 환경 없음), security-requirements.md의 NFR6.3, NFR6.8

| ID | 요구사항 | 측정 방법 | 출처 |
|---|---|---|---|
| NFR1.7 | 서비스는 상태 확인 경로(`GET /actuator/health`)를 제공하고 기동되면 UP을 돌려준다. | 단위/통합 시험 | 운영 규칙(상태 지표) |
| NFR1.8 | 요청마다 로그 한 줄에 메서드, 경로(쿼리 제외), 상태 코드, 처리 시간(ms), 세션 ID 앞 8자를 남긴다. 요청 본문, 서명 값, 전체 세션 ID는 기록하지 않는다. | 로그 출력 시험 | NFR1, NFR6.8 |
| NFR1.9 | 오류 응답(4xx, 5xx)은 오류 코드와 함께 로그에 남는다. 오류율은 로그에서 셀 수 있다. | 로그 출력 시험 | 운영 규칙(오류율 지표) |
| NFR5.12 | 웹훅 처리 결과마다 로그 한 줄에 결제 ID, 예약 ID, eventId, outcome, 결과(`APPLIED`, `DUPLICATE`, `IGNORED`, `UPSTREAM_FAILED`)와 처리 시간(ms)을 남긴다. 이 시간 값으로 시험이 NFR1.1을 확인한다. | 로그 출력 시험 | NFR5, NFR1 |
| NFR5.13 | 서명 거부, 승인 무시(IGNORED), u2 반영 실패, 모의 PG 자기 호출 실패는 각각 원인 코드(`INVALID_SIGNATURE`, `IGNORED`, `UPSTREAM_FAILED`, `PG_CALLBACK_FAILED`)와 함께 로그에 남는다. 서명 거부 로그에는 시각, 본문 길이, 이유만 있다. | 로그 출력 시험 | NFR6.3, NFR5.7 |
| NFR5.14 | 결제 요청 처리마다 로그 한 줄에 결제 ID(있으면), 예약 ID(있으면), 결과(`CREATED`, `REPLAYED`, 전달한 u2 오류 코드)와 시나리오를 남긴다. | 로그 출력 시험 | NFR5 |

## 범위 밖

- 외부 모니터링 시스템, 분산 추적, 대시보드, 알림은 이번 프로토타입에서 두지 않는다 (별도 배포 환경 없음). SLO 수치는 정하지 않으며 위 요구사항이 로컬에서 확인 가능한 최소 관측성이다.

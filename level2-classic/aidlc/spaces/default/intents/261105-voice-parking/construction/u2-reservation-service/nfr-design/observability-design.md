# u2-reservation-service 관측성 설계

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] nfr-requirements/observability-requirements.md(NFR1.5~NFR1.8, NFR5.9, NFR5.10), security-requirements.md(NFR6.6), phases/operation.md(상태 지표와 오류율 지표 최소 하나), team.md(별도 배포 환경 없음)

| 설계 | 방식 | 충족하는 요구사항 |
|---|---|---|
| 상태 확인 | Spring Boot Actuator의 `/actuator/health`만 노출한다. 다른 actuator 경로는 열지 않는다. | NFR1.5 |
| 요청 로그 필터 | 모든 요청이 끝날 때 로그 한 줄: 메서드, 경로(쿼리 제외), 상태 코드, 처리 시간(ms), 세션 ID 앞 8자. 구독 요청은 연결이 열릴 때 한 줄, 닫힐 때 한 줄을 남긴다. | NFR1.6 |
| 오류 로그 | 4xx·5xx 응답은 오류 코드(`code`)를 같은 로그 줄에 포함한다. 오류율은 로그에서 코드별로 센다. | NFR1.7 |
| 상태 전이 로그 | 상태가 바뀔 때 한 줄: 예약 ID, 이전 상태, 새 상태, version, 원인(`APPROVED`, `FAILED`, `AUTO_CANCEL`, `LATE_RESULT`). 예약 생성도 한 줄(원인 `CREATED`). | NFR5.9 |
| 거부·중복 로그 | 거부(`REJECTED`)와 중복 eventId 재응답은 예약 ID, eventId, outcome, 현재 상태와 함께 한 줄(원인 `REJECTED`, `DUPLICATE`). | NFR5.10 |
| 푸시 로그 | 푸시 한 건마다 예약 ID, version, 연결 수, 결과 반영 요청 수신부터 쓰기까지의 시간(ms)을 한 줄 남긴다. 구독 시작·종료와 쓰기 실패(원인 포함)도 예약 ID와 함께 남긴다. 시험이 이 시간 값으로 NFR1.1을 확인한다. | NFR1.8 |

## 로그 형식

한 줄에 `키=값` 쌍을 공백으로 잇는다(예: `event=state_change reservation=… from=PENDING_PAYMENT to=CONFIRMED version=2 cause=APPROVED`). 입력 원문, 전체 세션 ID, 쿼리 문자열은 기록하지 않는다(NFR6.6). 로그는 Spring Boot 기본 로깅(SLF4J)으로 표준 출력에 낸다.

## 범위 밖

- 외부 모니터링, 분산 추적, 대시보드, 알림, SLO 수치는 두지 않는다(별도 배포 환경 없음). 요청 간 상관 ID도 두지 않는다. 예약 ID가 이 서비스 안의 연결 고리다.

# u3-payment-service 관측성 설계

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] nfr-requirements/observability-requirements.md(NFR1.7~NFR1.9, NFR5.12~NFR5.14), security-requirements.md(NFR6.3, NFR6.8), phases/operation.md(상태 지표와 오류율 지표 최소 하나), team.md(별도 배포 환경 없음)

| 설계 | 방식 | 충족하는 요구사항 |
|---|---|---|
| 상태 확인 | Spring Boot Actuator의 `/actuator/health`만 노출한다. 다른 actuator 경로는 열지 않는다. | NFR1.7 |
| 요청 로그 필터 | 모든 요청이 끝날 때 로그 한 줄: 메서드, 경로(쿼리 제외), 상태 코드, 처리 시간(ms), 세션 ID 앞 8자(웹훅 요청은 세션이 없으므로 `-`). 요청 본문과 서명 값은 기록하지 않는다. | NFR1.8 |
| 오류 로그 | 4xx·5xx 응답은 오류 코드(`code`)를 같은 로그 줄에 포함한다. 오류율은 로그에서 코드별로 센다. | NFR1.9 |
| 웹훅 결과 로그 | 웹훅 처리 한 건마다 한 줄: `event=webhook payment=.. reservation=.. event_id=.. outcome=.. result=APPLIED\|DUPLICATE\|IGNORED\|UPSTREAM_FAILED ms=..`. 시험이 이 `ms`로 NFR1.1을 확인한다. | NFR5.12 |
| 원인 코드 로그 | 서명 거부(`INVALID_SIGNATURE`, 시각·본문 길이·이유만), 승인 무시(`IGNORED`), u2 반영 실패(`UPSTREAM_FAILED`), 모의 PG 자기 호출 실패(`PG_CALLBACK_FAILED`), 알림 포기(`PG_CALLBACK_DROPPED`)를 각각 결제 ID와 함께 남긴다. | NFR5.13 |
| 결제 요청 로그 | 결제 요청 한 건마다 한 줄: `event=payment_request payment=.. reservation=.. result=CREATED\|REPLAYED\|<u2 오류 코드> scenario=..`. | NFR5.14 |

## 로그 형식

한 줄에 `키=값` 쌍을 공백으로 잇는다. 입력 원문, 요청 본문, 서명 값, 전체 세션 ID, 쿼리 문자열은 기록하지 않는다(NFR6.8). 로그는 Spring Boot 기본 로깅(SLF4J)으로 표준 출력에 낸다.

## 범위 밖

- 외부 모니터링, 분산 추적, 대시보드, 알림, SLO 수치는 두지 않는다(별도 배포 환경 없음). 요청 간 상관 ID도 두지 않는다. 결제 ID와 예약 ID가 이 서비스 안의 연결 고리다.

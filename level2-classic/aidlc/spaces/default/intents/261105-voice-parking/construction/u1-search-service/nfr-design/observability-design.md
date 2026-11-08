# u1-search-service 관측성 설계

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] nfr-requirements/observability-requirements.md(NFR3.4~NFR3.6), security-design.md(입력 원문 비반사), phases/operation.md(상태 지표 하나, 오류율 지표 하나)

| 설계 | 방식 |
|---|---|
| 상태 지표 | Spring Boot Actuator의 `/actuator/health` 하나만 노출한다. 다른 actuator 경로는 노출하지 않는다. |
| 요청 로그 | 요청 처리를 감싸는 필터 하나가 응답이 끝나면 `경로, 상태 코드, 처리 시간(ms), 세션 ID 앞 8자`를 한 줄로 로그에 남긴다. 입력 문장 원문과 전체 세션 ID는 남기지 않는다. |
| 오류율 지표 | 4xx·5xx 응답은 오류 코드를 같은 로그 줄에 남긴다. 오류율은 로그에서 센다(별도 지표 수집기 없음). |
| 로그 형식 | 한 줄 key=value 텍스트. 구조화 로그 라이브러리를 추가하지 않는다. |
| 추적·알림·대시보드 | 두지 않는다(별도 배포 환경 없음). SLO 수치도 정하지 않는다. |

## 메모

이 설계가 충족하는 요구사항: NFR3.4(상태 확인), NFR3.5(요청 로그), NFR3.6(오류 로그).

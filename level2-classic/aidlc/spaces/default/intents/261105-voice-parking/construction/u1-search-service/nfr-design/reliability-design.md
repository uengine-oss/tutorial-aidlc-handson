# u1-search-service 신뢰성 설계

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] nfr-requirements/reliability-requirements.md(NFR2.1~NFR2.4), contract-summary.md(C1, C4, 오류·제한 시간 표), phases/construction.md(오류 처리)

| 설계 | 방식 | 충족하는 요구사항 |
|---|---|---|
| 빨리 실패하는 적재 | 기동 시 목업 JSON을 읽고 검증한다(필수 필드, 참조 무결성: 모든 주차권의 lotId가 존재, passId·lotId 유일). 하나라도 어긋나면 예외로 기동을 중단하고 로그에 이유를 남긴다. | NFR2.1 |
| 전역 예외 처리 | 한 곳의 예외 처리기가 검증 오류를 400/422로, 그 밖의 예외를 500 `INTERNAL_ERROR`와 공통 오류 형식으로 바꾸고 로그에 남긴다. 예외를 삼키지 않는다. 500 본문에는 내부 정보를 넣지 않는다. | NFR2.2 |
| 멱등 | C4는 읽기 전용이라 같은 요청을 반복해도 같은 응답이다. 호출자 u2의 재시도(200ms 간격 최대 2회)에 안전하다. | NFR2.3 |
| 회로 차단기·재시도 없음 | 이 서비스는 다른 서비스를 호출하지 않아 회로 차단기와 재시도를 둘 곳이 없다. 호출자 쪽(u2, u4) 정책은 해당 유닛이 설계한다. | NFR2.4 |
| 복구 | 서비스가 죽으면 u5-local-run 실행 스크립트로 다시 띄운다. 상태가 없어 복구 절차가 필요 없다. | NFR2.4 |

## 상태 확인

`GET /actuator/health`가 UP이면 목업 데이터가 적재된 상태다(적재 실패 시 기동 자체가 안 되므로 UP은 적재 완료를 뜻한다).

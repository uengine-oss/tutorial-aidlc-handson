# u1-search-service 성능 요구사항

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] requirements.md의 NFR3(추천 결과 2초 이내), functional-spec.md(W1), rules.md의 BR3.6, contract-summary.md(C1, C4의 2초 제한 시간과 재시도)

| ID | 요구사항 | 측정 방법 | 출처 |
|---|---|---|---|
| NFR3.1 | 추천 요청(C1)은 요청을 받은 시점부터 응답을 돌려줄 때까지 2초 이내여야 한다. | 로컬 환경에서 목업 데이터로 100회 반복한 응답 시간의 최댓값이 2초 이하 | NFR3, BR3.6 |
| NFR3.2 | 주차권 조회(C4)는 500ms 이내여야 한다. 호출자 u2-reservation-service의 제한 시간 2초와 200ms 간격 재시도 2회가 안쪽에서 끝나려면 한 번의 호출이 짧아야 한다. | 로컬 환경에서 100회 반복한 응답 시간의 최댓값이 500ms 이하 | NFR3, contract-summary.md의 제한 시간 표 |
| NFR3.3 | 서비스는 시작 후 목업 데이터를 메모리에 적재하며 요청마다 파일이나 외부 저장소를 읽지 않는다. | 요청 처리 중 외부 입출력이 없음을 단위 시험에서 확인 | NFR3 |

## 자원 제약

- 목업 데이터는 7건의 주차권이므로 요청당 계산 비용은 상수에 가깝다. 별도 자원 목표(메모리, CPU)는 정하지 않는다.
- 동시 사용자 수 목표는 정하지 않는다 (scalability-requirements.md 참조).

# External Dependency Map

## Sources

- [scope] Workflow-selected scope: classic
- [Q2] 데이터와 결제는 목업 데이터와 모의 결제 (requirements-analysis Q2)
- [memory:M1] bolt-plan.md, requirements.md, contract-summary.md, team-practices.md

Bolt는 작업 일부를 한 번 만들고 실행되는 결과로 끝내는 한 판이다. 이 문서는 팀 밖의 승인·자료·시스템 때문에 Bolt가 막힐 수 있는 항목을 적는다.

## 결과

시스템 밖에 의존하는 항목이 없다. 주차장·주차권 데이터는 시스템이 가진 목업 데이터이고, 결제는 payment-service 안의 모의 PG이며, 지도와 실제 결제대행사 연동은 하지 않는다. 승인이나 다른 팀의 인계도 없다.

| 항목 | 소유 | 소요 시간 | 막는 Bolt | 늦어질 때의 대응 |
|---|---|---|---|---|
| 해당 없음 | 해당 없음 | 해당 없음 | 해당 없음 | 해당 없음 |

## 내부적으로 확인이 필요한 항목

외부는 아니지만 아직 확정되지 않아 Bolt에 영향을 줄 수 있는 항목이다.

| 항목 | 영향받는 Bolt | 대응 |
|---|---|---|
| Vite 프록시가 SSE를 버퍼링하지 않는지 | B4, B5 | B4에 시험을 넣는다. 버퍼링되면 U4의 연결 방식을 다시 정한다 |
| 구간별 1초 예산 값(웹훅 150ms, 반영·푸시 150ms, 프록시와 표시 200ms) | B2, B3, B4 | Functional Design에서 시험 기준으로 확정한다 |
| Java와 Node의 로컬 개발 환경(JDK, 빌드 도구, Node)이 이 머신에 있는지 | B1~B5 | 첫 Bolt 전에 확인한다. 없으면 설치가 필요하다 (팀 규칙은 Docker 금지) |

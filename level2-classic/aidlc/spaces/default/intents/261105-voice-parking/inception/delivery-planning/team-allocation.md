# Team Allocation

## Sources

- [scope] Workflow-selected scope: classic (팀 구성 단계 없음)
- [Q1] Bolt는 유닛당 하나, 총 5개
- [memory:M1] bolt-plan.md, unit-of-work.md, team-practices.md (Bolt는 한 번에 하나씩 순서대로)

Bolt는 작업 일부를 한 번 만들고 실행되는 결과로 끝내는 한 판이다. 이 문서는 Bolt를 누가 맡는지 적는다.

## 배정

classic 범위에는 팀 구성 단계가 없다. 따라서 모든 Bolt를 AI 개발 에이전트(aidlc-developer-agent) 한 명이 맡고, 사람이 각 Bolt 끝에서 결과를 확인한다. 팀(mob: 여러 사람이 함께 한 작업을 맡는 모음)은 나누지 않는다.

| Bolt | 유닛 | 담당 | 사람의 확인 |
|---|---|---|---|
| B1 | U1 u1-search-service | aidlc-developer-agent | Bolt 끝 확인 |
| B2 | U2 u2-reservation-service | aidlc-developer-agent | Bolt 끝 확인 |
| B3 | U3 u3-payment-service | aidlc-developer-agent | Bolt 끝 확인 |
| B4 | U4 u4-web-ui | aidlc-developer-agent | Bolt 끝 확인 |
| B5 | U5 u5-local-run | aidlc-developer-agent | Bolt 끝 확인 |

## 실행 방식

- 한 번에 Bolt 하나씩 순서대로 만든다 (직렬). 유닛이 모두 사슬로 이어져 있어 동시에 만들 수 있는 쌍이 없다.
- 팀 규칙에 따라 트렁크(`main`) 하나를 쓰고, Bolt 하나가 끝날 때마다 `main`에 한 건의 커밋으로 합친다 (Bolt 이름이 커밋 이름).
- 프로그램 보드(팀이 여럿일 때 Bolt를 팀에 배분하는 표)는 팀이 하나뿐이므로 두지 않는다.

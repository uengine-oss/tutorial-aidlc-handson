# u1-search-service 확장성 설계

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] nfr-requirements/scalability-requirements.md(NFR4.1, NFR4.2), team.md의 Deployment(별도 배포 환경 없음)

| 설계 | 방식 | 충족하는 요구사항 |
|---|---|---|
| 동시 요청 10건 | Spring Boot 기본 서블릿 스레드 풀(기본 200)로 충분하다. 요청 처리는 CPU만 쓰는 짧은 계산이라 별도 풀을 두지 않는다. 동시 10건 시험으로 확인한다. | NFR4.1 |
| 무상태 | 요청 사이에 바뀌는 상태가 없다. 목업 데이터는 시작 후 불변이다. 그래서 인스턴스를 늘려도 같은 결과가 나온다. 이번에는 한 개만 띄운다. | NFR4.2 |

자동 확장, 부하 분산, 데이터 분할은 설계하지 않는다(별도 배포 환경이 없고 데이터가 7건 고정이다).

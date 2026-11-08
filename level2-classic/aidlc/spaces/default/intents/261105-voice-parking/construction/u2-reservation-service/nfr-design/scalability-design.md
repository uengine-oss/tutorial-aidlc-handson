# u2-reservation-service 확장성 설계

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] nfr-requirements/scalability-requirements.md(NFR4.1~NFR4.4), tech-stack-decisions.md(NFR4.5), rules.md(BR4.4, BR5.2, BR5.3), components.md(Reservation, Notification 경계)

| 설계 | 방식 | 충족하는 요구사항 |
|---|---|---|
| 세분화된 잠금 | 예약 ID별 잠금(상태 판정), 세션 ID별 잠금(예약 생성)을 `ConcurrentHashMap`으로 관리한다. 전역 잠금은 없다. 서로 다른 예약·세션의 처리는 서로를 막지 않는다. | NFR4.2 |
| 데모 규모 스레드 | 서블릿 스레드 풀은 기본값을 쓴다. 푸시 실행기는 스레드 상한 32(동시 구독 수 상한과 같음), 취소 스케줄러 스레드 2개, 감시 스케줄러 스레드 1개다. 동시 10 세션 시험(느린 클라이언트 포함)으로 목표를 확인한다. | NFR4.1 |
| 단일 프로세스 | 상태가 프로세스 메모리에 있으므로 서비스는 한 개만 띄운다. 두 번째 인스턴스를 같은 포트로 띄우면 포트 충돌로 기동이 실패한다(포트 8082 고정). | NFR4.3 |
| 구독 정리 | 구독은 예약 ID별 목록에 두고, 연결이 끝나거나 쓰기가 실패하면 그 항목만 제거한다. 완료·오류·시간 초과 콜백에서 모두 제거한다. | NFR4.4 |
| 패키지 경계 | Reservation 패키지와 Notification 패키지를 나눈다. Reservation이 Notification의 푸시 인터페이스(스냅샷을 받는 한 메서드)를 부르고, Notification은 Reservation을 참조하지 않는다. 구독 엔드포인트의 소유 확인과 대체 스냅샷 생성은 두 패키지 바깥의 웹 계층이 맡는다. | NFR4.5 |

## 확장하지 않는 것

- 여러 인스턴스, 영속 저장소, 끝난 예약의 정리는 설계하지 않는다(NFR4.3, 범위 밖). 확장이 필요하면 저장소를 공유 저장소로, 타이머와 구독을 외부 구성 요소로 옮기는 별도 변경이다.

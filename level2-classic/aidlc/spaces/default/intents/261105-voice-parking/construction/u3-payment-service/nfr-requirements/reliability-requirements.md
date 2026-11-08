# u3-payment-service 신뢰성 요구사항

## Sources

- [scope] Workflow-selected scope: classic
- [Q3] 비동기 웹훅 승인 (requirements-analysis Q3)
- [memory:M1] requirements.md의 NFR5, rules.md의 BR1.2~BR1.5, BR2.1, BR3.4~BR3.6, BR5.1, contract-summary.md(C5, C6의 제한 시간·재시도·502), phases/construction.md(오류 처리: 통합 경계에서 오류 처리, 조용한 실패 금지, 복구 가능과 치명적 구분)

| ID | 요구사항 | 측정 방법 | 출처 |
|---|---|---|---|
| NFR5.1 | 웹훅 처리는 멱등하다. 같은 eventId가 몇 번, 동시에 와도 u2 호출은 정확히 1회이고 둘째부터는 DUPLICATE로 응답한다. eventId는 u2 반영이 성공한 뒤에만 처리한 것으로 기록한다. | 같은 eventId 3회(순차)·동시 3건 시험에서 u2 호출 1회 | NFR5, BR3.4 |
| NFR5.2 | u2 반영이 실패하면(재시도 후에도 응답 없음, 오류 응답) 502 UPSTREAM_FAILED를 돌려주고 eventId를 기록하지 않으며 Payment.status를 바꾸지 않는다. 같은 이벤트가 다시 오면 처음부터 처리한다. | u2를 지연·오류시키는 가짜로 시험 | NFR5, BR3.6 |
| NFR5.3 | u2 결과 반영 호출은 제한 시간 2초, 200ms 간격 최대 2회 재시도(같은 eventId라 안전)를 따른다. | 가짜 u2로 호출 횟수·간격 확인 | NFR5, contract-summary.md |
| NFR5.4 | u2 예약 생성 호출은 제한 시간 1.5초이고 자동 재시도를 하지 않는다. 실패하면 502 UPSTREAM_FAILED를 돌려주고 Payment를 만들지 않는다. 사용자가 같은 멱등 키로 다시 요청하면 이어 갈 수 있다. | 가짜 u2로 시험: 호출 1회, 502 확인 | NFR5, BR1.4 |
| NFR5.5 | 같은 (세션, 멱등 키)의 결제 요청이 동시에 와도 Payment는 1건, 모의 PG 요청은 1회다. Payment 조회·생성과 모의 PG 요청은 키 단위로 원자적이다. | 같은 키 동시 3건 시험 | NFR5, BR1.3 |
| NFR5.6 | 이미 종료된 Payment(APPROVED, FAILED, IGNORED)에 새 eventId의 웹훅이 와도 Payment.status를 바꾸지 않는다. u2에는 전달하고 REJECTED이면 WebhookEvent만 IGNORED로 기록한다. | 종료된 Payment에 새 eventId 시험 | NFR5, BR3.5 |
| NFR5.7 | 모의 PG의 알림 호출이 실패하면(연결 거부, 오류 응답) 다시 보내지 않고 오류를 결제 ID와 함께 로그에 남긴다. 승인이 유실되면 예약은 u2가 180초 뒤 자동 취소한다. | 웹훅 주소를 닫아 둔 시험에서 오류 로그 확인 | NFR5, BR2.1, BR3.6 |
| NFR5.8 | 처리하지 못한 예외는 500 `INTERNAL_ERROR`와 공통 오류 형식으로 돌려주고 서버 로그에 남긴다. 조용히 삼키지 않는다. 상태 변경 중 예외가 나면 Payment와 WebhookEvent는 변경 전 상태로 남는다. | 강제 예외 시험 | 오류 처리 규칙 |
| NFR5.9 | 서비스를 다시 시작하면 결제 기록, 처리한 이벤트, 예약된 모의 PG 알림이 사라진다. 사라진 결제에 대한 웹훅은 404 UNKNOWN_PAYMENT다. | 재시작 후 웹훅 시험 | NFR8, BR5.1 |
| NFR5.10 | 가용성 목표(SLA)는 정하지 않는다. 프로토타입이며 로컬에서만 동작한다. 서비스가 죽으면 실행 스크립트로 다시 띄운다. | 설계 확인 | classic 범위 |
| NFR5.11 | 모의 PG의 시간 기반 동작(지연 알림)은 주입한 시계와 스케줄러로 제어해 실제 대기 없이 시험할 수 있다. | 시계 주입 단위 시험 | BR2.1, NFR7 |

## 장애 시 동작

| 장애 | 동작 |
|---|---|
| u2 중단·지연 (결제 요청) | 1.5초 안에 502. Payment 없음. 같은 키로 다시 요청 가능 |
| u2 중단·지연 (웹훅) | 최대 약 6.4초 뒤 502, 이벤트 미기록. 모의 PG는 재전송하지 않으므로 승인이 유실될 수 있고 예약은 u2가 자동 취소한다 |
| 모의 PG 자기 호출 실패 | 오류 로그. 재전송 없음 |
| 이 서비스 재시작 | 결제 기록과 예약된 알림 소실. 예약은 u2가 자동 취소한다 |

## Assumptions & Open Questions

- [assumption] 인메모리 저장소이므로 재시작 시 상태를 잃는 것과, 모의 PG가 재전송하지 않아 승인이 유실될 수 있는 것을 정상 동작으로 본다. 근거: 프로토타입이며 요구사항이 영속성이나 재전송을 요구하지 않는다(contract-summary.md C6).
- 열린 질문: 없음.

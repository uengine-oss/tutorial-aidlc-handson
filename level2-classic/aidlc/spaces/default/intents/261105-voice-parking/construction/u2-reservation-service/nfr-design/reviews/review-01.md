## Review

**Verdict:** NOT-READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-07T12:25:05Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-design/performance-design.md > 짧은 임계 구역 / 구독 시작 | 구독 시작 행은 "예약 단위 잠금 안에서 연결을 등록하고 ... 최신 스냅샷을 읽어 첫 이벤트로 보낸다"고 쓰고, "새 푸시는 같은 잠금을 거쳐 순서대로 나간다"고 한다. 그러나 짧은 임계 구역 행은 "스냅샷 직렬화와 연결 쓰기는 잠금을 놓은 뒤 푸시 실행기에서 한다"고 한다. 두 문장은 양립하지 않는다. 첫 이벤트 쓰기가 잠금 안이면, 느린 클라이언트의 쓰기(상한 1초)가 같은 예약의 결과 반영·자동 취소 판정을 최대 1초 막아 NFR1.1/NFR2.2의 150ms를 깬다. 잠금 밖이면 "잠금을 거쳐 순서대로 나간다"는 순서 보장 근거가 사라진다. | 첫 이벤트를 어느 스레드에서 어느 잠금 상태로 쓰는지 하나로 정한다. 예: 잠금 안에서는 등록과 최신 스냅샷 읽기만 하고 쓰기는 잠금 밖에서 연결별 직렬 큐로 한다. 이에 맞춰 두 행의 문장을 고친다. | New |
| R-02 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-design/performance-design.md > 푸시 실행기 분리 / 구독 시작; logical-components.md > Notification | 푸시 실행기는 스레드 4개다. 같은 예약의 v2, v3 푸시가 서로 다른 스레드에 배정되면 같은 연결에 v3가 v2보다 먼저 쓰일 수 있다. 연결별 순서 보장은 어디에도 없다. BR4.1은 "같은 예약의 스냅샷은 version 순서대로 전달"을 요구한다. BR4.2의 version 비교는 Notification 보관 시점만 걸러 주고 연결 쓰기 순서는 보장하지 못한다. `SseEmitter.send`는 스레드 안전하지 않은데, 동시에 쓰는 푸시 스레드와 keep-alive(BR4.5) 쓰기를 직렬화하는 설계도 없다. 또 Notification의 최신 스냅샷 저장이 잠금 안인지 실행기 안인지 정해져 있지 않다. 실행기 안이면 구독이 v1을 읽고 등록하는 동안 v2 푸시가 이미 구독 목록 순회를 마친 경우 v2를 놓칠 수 있다. 이 경우 "등록 후에 읽으므로 사이에 일어난 변화를 놓치지 않는다"는 주장은 성립하지 않는다. | 연결(emitter)별 직렬 쓰기 큐(또는 연결별 잠금)와 그 안에서 version 비교를 설계에 추가한다. 최신 스냅샷 갱신과 구독자 목록 스냅샷 취득을 예약 잠금 안에서 함께 하는지 명시한다. 푸시와 keep-alive 쓰기를 직렬화하는 방법을 적는다. 순서 역전과 등록 경합을 재현하는 시험 항목을 추가한다. | New |
| R-03 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-design/reliability-design.md > 푸시 실패 격리; performance-design.md > 푸시 실행기 분리 | "취소 타이머와 푸시가 분리되어 쓰기가 느려도 취소 타이머가 늦어지지 않는다"는 주장은 상태 확정에만 맞다. 취소 스냅샷의 쓰기(NFR2.2, 150ms)는 같은 4개 스레드 푸시 실행기를 쓴다. 쓰기 하나의 상한이 1초이므로 느린 연결 4개가 스레드를 모두 잡으면 다른 예약의 승인 푸시(NFR1.1)와 취소 푸시가 최대 1초 대기한다. 실행기 큐의 크기, 거부 정책, 제출 시 블로킹 여부도 정해져 있지 않다. 동시 10 세션 시험(NFR4.1)이 느린 클라이언트를 포함하지 않으면 이 경로가 검증되지 않는다. 또한 NFR4.2의 "다른 예약은 서로 막지 않는다"에도 어긋난다. | 쓰기 상한을 NFR 목표에 맞게 줄이거나(예: 100~150ms) 느린 연결을 격리하는 방식을 정한다. 실행기 큐 상한과 거부 정책, 제출이 호출 스레드를 막지 않는다는 점을 명시한다. 느린 클라이언트 1~4개가 있어도 다른 예약의 푸시가 150ms 안에 나가는 시험을 추가한다. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-design/performance-design.md > u1 호출 마감 | 전체 마감 1.5초에 시도 500ms와 간격 200ms를 적용하면 시도 시작 시각은 0, 700ms, 1400ms다. 1400ms 시점의 남은 시간이 100ms라 200ms 미만 규칙으로 세 번째 시도는 시작되지 않는다. 실제 최대 시도는 2회다. 계약 C4와 NFR5.5, BR1.4는 "최대 2회 재시도"(총 3회 호출)이고 NFR5.5의 시험도 "호출 횟수 최대 3회"다. u3의 2초 제한과 총시간은 충돌하지 않아 이전 단계의 총 소요 시간 우려는 해소됐다. 그러나 이 문서가 C4의 재시도 횟수를 조용히 줄이고 있고, 시도 제한 500ms는 u1 자체 목표(500ms)와 같아 정상 응답이 지연 한 번에 시간 초과로 처리될 수 있다. | 시도 제한과 간격을 조정해 3회가 가능하게 하거나(예: 시도 350ms, 간격 200ms), "실질 최대 2회 호출"을 계약과의 의도적 차이로 명시한다. NFR5.5 시험 문구(최대 3회)를 이에 맞춘다. | New |
| R-05 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-design/security-design.md > 내부 경로 노출 차단 / Assumptions & Open Questions | 이전 단계의 `/internal/**` 무인증 우려는 루프백 바인딩으로 완화됐지만 해소된 것은 아니다. 같은 머신의 어느 프로세스든 서명 없이 `payment-result`로 예약을 CONFIRMED로 만들 수 있다. 이는 [assumption]으로 기록돼 있다. 시험은 `server.address` 설정값만 확인하고 실제 접속 거부는 확인하지 않는다. "u3가 호출하는 u2 주소도 루프백 주소다"는 u3/u5 쪽 의무인데 이 단위 안에서는 확인할 수 없다. | 잔여 위험을 승인 시점에 사람이 판단하도록 명확히 보인다. 시험에 실제 바인딩 확인(비루프백 주소 접속 실패)을 포함한다. u3/u5에 전달할 의무로 따로 기록한다. | New |
| R-06 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-design/security-design.md > 경계 입력 검증 | "어긋나면 세션은 401, 그 밖은 400"이라고 쓴다. 그러나 BR1.1은 `POST /internal/reservations`의 sessionId 형식 오류를 400 INVALID_REQUEST로 정한다. 401은 BR3.1의 조회·구독 엔드포인트에만 해당한다. 또 passId·idempotencyKey의 100자 상한은 BR1.1과 계약에 없는 새 제약이며, 그 근거가 [assumption]뿐이라 u3가 긴 키를 보내면 400이 된다. | 엔드포인트별로 401과 400을 구분해 적는다. 100자 상한이 계약(C5)에 없다는 점을 u3에 알리거나 근거를 계약에서 찾는다. | New |
| R-07 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-design/ (reliability-design.md, performance-design.md, scalability-design.md) | 세션 잠금과 예약 잠금을 함께 쥐는 경로의 획득 순서가 문서화돼 있지 않다. 자동 취소 타이머를 예약이 결제 대기를 벗어날 때 해제하는 설계(BR2.4, 성능 요구사항의 자원 제약)도 없다. 타이머 취소를 예약 잠금 안에서 하는지, 이미 실행 중인 타이머와 어떻게 만나는지도 정해져 있지 않다. 연결 유지 신호(BR4.5, 15초)와 SseEmitter 시간 초과 값이 어느 스레드에서 어떤 설정으로 동작하는지도 없다. Spring MVC 기본 비동기 시간 초과(예: Tomcat 30초)면 180초 대기 중 연결이 반복해서 끊긴다. | 잠금 획득 순서(예: 세션 잠금 → 예약 잠금, 역순 금지)와 타이머 해제 방식, SseEmitter 시간 초과 값, keep-alive 담당 스레드를 설계에 한 줄씩 추가한다. | New |
| R-08 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-design/traceability.json > upstream_ids | `upstream_ids`에 NFR1.5~NFR1.8, NFR5.9, NFR5.10, NFR4.5, NFR7.1, NFR8.2, NFR9.1이 없다. 이 중 관측성(NFR1.5~1.8, 5.9, 5.10), NFR4.5, NFR7.1은 설계 본문이 이미 다루고 있어 추적 표가 설계 범위를 다 담지 못한다. 시험성 측면에서는, 주입 시계를 쓰는 시험이 `Clock`만 앞으로 돌리므로 실제 `TaskScheduler`가 같은 시계로 발화하는지(NFR2.1의 타이머 경로)는 시험하지 못한다. | 빠진 NFR을 coverage에 추가한다. 스케줄러가 주입 시계를 쓰도록 하는 방법(예: `ThreadPoolTaskScheduler.setClock`) 또는 타이머 콜백을 직접 호출하는 시험 방법을 적는다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 수동: traceability.json upstream_ids 27개와 nfr-requirements 대조 | 27개 모두 nfr-requirements/*.md 또는 tech-stack-decisions.md에 존재하고 coverage 행이 27개 있다 | 형식 통과. 다만 위 Minor대로 NFR1.5~1.8, 5.9, 5.10, 4.5, 7.1, 8.2, 9.1은 upstream_ids에 없다 |
| 수동: OK 대상이 실제 설계를 가리키는지 | 지정한 설계 문장이 해당 파일에 있다. 다만 NFR1.1, NFR1.3, NFR2.2의 대상 설계는 위 Major 1~3 때문에 요구 수치를 달성한다고 보장하지 못한다 | 항목 존재는 확인, 달성 근거는 불충분 |
| 수동: 기능 설계 규칙 대조 | BR1.2→BR1.3 순서(reliability-design "직렬화"), BR2.3 시계 기반 마감, BR5.2 직렬화는 일치. BR4.3 구독 순서와 BR4.1 version 순서는 연결 쓰기 단계에서 보장이 없음(Major 1, 2). 중복 eventId 로그는 BR2.1이 금지하지 않고 NFR5.3이 요구하므로 모순 아님 | 위 Major 참조 |
| 수동: 계약 C4/C5 대조 | 클라이언트 마감 1.5초는 계약의 2초보다 짧아 호출자 2초 안에 502까지 끝난다. 그러나 C4의 "최대 2회 재시도"는 실질 1회로 줄어든다 | Minor로 기록 |

### Summary

푸시 경로의 동시성 설계(구독 시작의 잠금 범위, 연결별 쓰기 순서, 4개 스레드 공유 실행기와 1초 쓰기 상한)가 스스로 모순되거나 비어 있어 150ms 목표와 version 순서 보장을 설계만으로는 달성한다고 볼 수 없다. u1 호출 총시간과 루프백 바인딩은 이전 단계의 Major 우려를 대체로 완화했으나, C4의 재시도 횟수 축소와 `/internal` 무인증 잔여 위험은 사람이 확인할 사항이다.

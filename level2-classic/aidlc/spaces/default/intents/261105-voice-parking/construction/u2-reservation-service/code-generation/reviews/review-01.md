## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-07T13:17:12Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | services/u2-reservation-service/src/test/java/kr/voiceparking/reservation/PerformanceTest.java > oneRun (느린 클라이언트), services/u2-reservation-service/src/main/java/kr/voiceparking/reservation/notification/SseChannel.java > close, NotificationService.java > closeChannelLater | "막힌 쓰기" 시험(느린 클라이언트 1~4개, 쓰기 1초 초과, 큐 초과)은 모두 `FakeChannel.blockWrites()`로 만든 가짜 채널을 쓴다. 실제 `SseEmitter`에 TCP 수신 버퍼가 찬(읽지 않는) 소켓을 붙인 시험은 없다(SseWireTest는 정상 스트림, 오류 JSON, 연결 끊김 3건뿐). code-summary.md의 "읽지 않는 클라이언트 1개 113ms(150)"은 실제 소켓을 읽지 않는 클라이언트가 아니라 가짜 채널이 막힌 경우를 잰 값이다. 또 `SseChannel.close`가 부르는 `emitter.completeWithError`가 진행 중인 `emitter.send`와 같은 잠금을 기다리면, 막힌 쓰기 스레드가 풀리기 전까지 "push-closer" 스레드도 막힌다. 그 사이 감시가 구독 슬롯(32)을 먼저 반환하므로 막힌 배출 스레드가 최대 64개까지 쌓이고 새 연결이 EXECUTOR_REJECTED로 닫힐 수 있다. D5가 의존하지 않기로 한 "막힌 스레드 반환"에 실제 경로가 여전히 걸려 있는지 확인되지 않았다(검토에서 Spring 내부 잠금 동작은 확인하지 못했다). | 읽지 않는 실제 소켓(작은 수신 버퍼, 읽기 중단)을 SseEmitter에 붙여 큐 초과, 1초 쓰기 초과, 이어진 푸시 지연(150ms)을 확인하는 시험을 하나 추가하거나, 그렇지 못하면 code-summary.md의 "읽지 않는 클라이언트" 표현을 "가짜 채널로 막은 쓰기"로 고치고 이 경로를 검증되지 않은 위험으로 적는다. 막힌 쓰기 스레드가 구독 슬롯 반환 뒤에도 남는 수를 상한으로 막을지(예: 막힌 연결 수를 세어 새 구독 거절) 결정한다. | New |
| R-02 | Minor | services/u2-reservation-service/src/main/java/kr/voiceparking/reservation/web/ApiExceptionHandler.java > handleUnexpected; SseChannel.java > close | 서버가 연결을 끊을 때 `completeWithError(new IOException("closed by server: ..."))`가 일반 예외 처리기로 들어가 ERROR 로그("unhandled error type=IOException", 스택 포함)가 남고 500 INTERNAL_ERROR 본문을 이미 열린 event-stream 응답에 쓰려 한다. `mvn verify` 출력에서 "closed by server: WRITE_FAILED" 줄이 확인된다(`java.io.IOException: closed by server: WRITE_FAILED at SseChannel.close(SseChannel.java:38)`). 의도된 정상 정리(큐 초과, 쓰기 시간 초과)가 오류율 지표와 로그를 오염시킨다. | 서버가 의도해 끊은 경우와 클라이언트 끊김(IOException, AsyncRequestNotUsableException)은 `handleUnexpected`에서 걸러 INFO/WARN 한 줄로 남기고 본문을 쓰지 않는다. | New |
| R-03 | Minor | services/u2-reservation-service/src/test/java/kr/voiceparking/reservation/PerformanceTest.java > assertMaxWithin | 100회 최댓값 한도를 넘으면 같은 시험을 한 번 더 실행해 두 번 모두 넘어야 실패로 본다. NFR 목표를 "최댓값이 한도 이내"로 읽으면 첫 실행 초과를 한 번 지우는 완화이며, 요약의 "어느 목표도 완화하지 않았다"와 어긋난다. 통과한 실행에서 재실행 여부는 `PERF ... 한 번 다시 실행` 출력으로만 알 수 있다. | 재실행 허용을 NFR 쪽에 명시해 정당화하거나(예: JVM 정지 제외 규칙), 재실행 없이 한 번에 통과하도록 바꾼다. 요약에는 재실행 규칙을 적는다. | New |
| R-04 | Minor | services/u2-reservation-service/src/main/java/kr/voiceparking/reservation/reservation/ReservationService.java > cancelOnDeadline | 타이머 콜백이 현재 시각이 마감 시각 이상인지 확인하지 않고 `current.cancel(now())`로 확정한다. 스케줄러가 밀리초 단위로 일찍 깨면 `cancelledAt`이 마감(paymentRequestedAt+180초)보다 앞서고, 그 직전 결과 요청이 승인될 수도 있었던 경계가 흐려진다(BR2.3 판정은 결과 경로에서만 `>=`로 정확하다). | 콜백에서 `now()`가 마감 이전이면 취소하지 않고 마감 시각으로 다시 예약하거나 `cancelledAt`을 `max(now, deadline)`으로 둔다. | New |
| R-05 | Minor | services/u2-reservation-service/src/main/java/kr/voiceparking/reservation/reservation/ReservationService.java > create | 같은 `idempotencyKey`로 passId·durationHours가 다른 요청이 와도 검증 없이 기존 예약을 200으로 돌려준다. 계약상 금지는 아니지만, u3가 키를 잘못 재사용하면 다른 주차권의 예약을 조용히 받는다. | 같은 키에 다른 내용이 오는 경우의 동작(그대로 200 또는 409/400)을 정하고 시험 한 건으로 고정하거나, 현재 동작이 의도임을 code-summary.md에 적는다. | New |
| R-06 | Minor | services/u2-reservation-service/src/main/resources/application.properties > server.address; InternalReservationController | `/internal/**`(C5)이 공개 `/api/**`와 같은 포트에 있고 방어는 `server.address=127.0.0.1` 하나다. `SERVER_ADDRESS`를 바꾸면 경로 수준의 추가 방어 없이 내부 경로가 열린다. 설계(NFR6.5)가 인정한 선택이고 요약이 u3·u5에 알리지만, 환경 변수 한 줄로 해제된다. | 비루프백 주소로 기동될 때 시작 로그에 경고 한 줄을 남기거나, `/internal/**` 요청의 원격 주소가 루프백인지 필터로 확인한다. | New |
| R-07 | Minor | services/u2-reservation-service/src/main/java/kr/voiceparking/reservation/notification/NotificationService.java > states; ReservationStore.java > byId, eventsByReservation, reservationLocks, sessionLocks | 종료된 예약의 스냅샷 상태, 판정 기록, 예약·세션 잠금 객체가 프로세스 수명 동안 제거되지 않는다. 종료 상태를 받은 뒤 연결을 닫는 일은 화면에 맡겨져 있어 서버는 10분 emitter 시간 초과까지 구독 슬롯(32)을 쥔다. 로컬 시연 규모에서는 문제가 아니지만 설계된 인메모리 동작이라고만 적혀 있고 증가 한계는 적혀 있지 않다. | code-summary.md의 알려진 사항에 "항목이 제거되지 않음, 시연 규모 전제"를 적거나, 종료 상태 스냅샷 전송 뒤 서버가 emitter를 완료하도록 한다. | New |
| R-08 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/code-generation/traceability.json > BR1.5, BR2.4, AC4.2.1, BR4.6 | 일부 OK 대상이 ID를 실제로 구현하는 파일이 아니다. BR1.5(새 예약 version 1, 마감 계산)는 `domain/Reservation.java`에 있는데 `ReservationService.java`로 걸려 있고, BR2.4·AC4.2.1의 취소 판정(`cancelOnDeadline`)은 `ReservationService.java`에 있는데 `DeadlineTimers.java`(타이머 등록만)로 걸려 있으며, BR4.6(푸시 150ms)은 시험 파일 하나로만 걸려 있다. | BR1.5는 Reservation.java, BR2.4·AC4.2.1은 ReservationService.java와 DeadlineTimers.java 둘 다, BR4.6은 구현 쪽 파일과 시험을 함께 가리키게 고친다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `mvn -q -pl services/u2-reservation-service test` | 종료 코드 0. surefire 보고서 합계 145건, 실패·오류·건너뜀 0(21개 클래스: 예 ReservationServiceCreateTest 24, NotificationServiceTest 15, ReservationServiceApplyTest 14) | 요약의 145개 통과 주장과 일치한다. 이 실행에서 slow 시험은 제외됐다(target/의 RealDeadlineSlowTest 189.5초 기록은 이전 실행의 것이며 이번에 재실행하지 않았다). |
| `mvn -q -pl services/u2-reservation-service verify` | 종료 코드 0, JaCoCo 라인 80% 하한 검사 통과(pom: BUNDLE LINE COVEREDRATIO 0.80, 제외 규칙 없음) | 하한이 약화되지 않았다. 요약의 96.6%는 보고서 값을 따로 확인하지 못했지만 하한 통과는 확인했다. |
| `git status --short services/u1-search-service pom.xml` | u1-search-service는 작업 트리 전체가 추적되지 않는 상태(`??`)로 변경 이력 비교는 불가 | u1이 수정되지 않았다는 주장은 이 상태에서 확인할 수 없다. 이 유닛의 코드는 u1 소스를 import하지 않는다. |

### Summary

상태 기계, 180초 마감(`>=`로 취소, 결과 경로의 지연 취소와 `cancelledAt`=마감 시각), 멱등 키 우선 확인과 u1 호출의 잠금 밖 배치, 예약 잠금 안의 입출력 없는 발행과 연결별 FIFO·단일 배출, 잠금 순서(세션, 예약, 알림 모니터, 연결)는 코드에서 일관되며 교착 경로를 찾지 못했다. 시험 145건과 80% 하한도 확인했다. 가장 큰 위험은 막힌 쓰기·느린 클라이언트 경로가 실제 SseEmitter 소켓이 아니라 가짜 채널로만 검증됐다는 점이므로 승인 전에 시험을 보강할지 위험으로 기록할지 정해야 한다.

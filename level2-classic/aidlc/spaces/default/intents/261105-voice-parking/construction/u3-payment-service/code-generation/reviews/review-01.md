## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-07T14:28:07Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | services/u3-payment-service/src/main/java/kr/voiceparking/payment/payment/PaymentService.java > settleRequest (74~91행) | 같은 (세션, 키)로 u2가 새 예약(201)을 돌려줬는데 로컬에 Payment가 이미 있으면 기존 Payment를 그대로 쓴다(`existing.orElseGet(...)`). 이때 Payment.reservationId는 옛 값이고 `hasPgRequest`가 참이라 모의 PG 등록은 건너뛰지만, 응답에는 새 `reply.reservationId()`가 실린다. u2는 독립적으로 재시작할 수 있는 인메모리 서비스라 u2 재시작 뒤 같은 키 재요청에서 실제로 일어날 수 있다. 그러면 새 예약에는 승인 알림이 오지 않아 180초 뒤 자동 취소되고, 옛 예약으로 가는 웹훅은 u2에서 404가 되어 502로 끝난다. code-summary.md도 이 경우를 "재사용한다"고만 적고 후속 결과는 밝히지 않는다. | `existing.reservationId()`와 `reply.reservationId()`가 다르면 재사용하지 말고 의도를 정한다. 예: 새 예약으로 Payment를 다시 연결하고 모의 PG를 다시 등록하거나, 명시적 오류(예: 502 UPSTREAM_FAILED)로 끝낸다. 그 경우를 다루는 시험을 추가하고 code-summary.md에 결과를 적는다. | New |
| R-02 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u3-payment-service/code-generation/traceability.json > NFR1.1~NFR1.4, NFR4.1, NFR4.5, BR3.7 | 이 ID들의 target이 `PerformanceTest.java`이고 status가 `OK`인데, 계획서 Step 15는 미완료이고 code-summary.md도 중복 웹훅 20ms·서명 거부 20ms 두 건이 이 머신에서 간헐적으로 실패한다고 적는다. 추적성만 보면 성능 목표가 검증된 것으로 읽힌다. 이 리뷰는 PerformanceTest를 실행하지 않았으므로 통과 여부를 확인하지 못했다. | 사람이 부하가 낮을 때 `verify`를 다시 돌려 통과를 확인한 뒤에 `OK`로 둔다. 그 전에는 해당 항목에 미확인임을 표시한다. | New |
| R-03 | Minor | services/u3-payment-service/src/test/java/kr/voiceparking/payment/PerformanceTest.java > measure / assertMaxWithin (84~114행) | 측정이 실제 Tomcat 소켓이 아니라 `MockMvc` 안에서 이루어진다. 그래서 중복 20ms·서명 거부 20ms는 서블릿 스택과 네트워크를 거치지 않은 수치다. 재실행 규칙은 계획 E6과 코드가 일치한다. 다만 중앙값이 0.1~1ms이므로 "중앙값의 5배 이상"은 거의 항상 참이라 단발 이상값 판별 구실을 못하고, 사실상 같은 조건으로 한 번 더 도는 엄격한 두 번째 시도가 된다. 100회 최댓값은 공유 호스트에서 본래 불안정한 통계라 flaky가 구조적이다. | 목표를 약화하지 않는 선에서 한 가지를 택한다. 실제 HTTP 호출로 측정하거나, 최댓값 대신 높은 백분위(예: p99)와 최댓값을 따로 판정하도록 NFR 문구를 맞춘다. 사람이 이미 정한 "엄격 유지 후 부하 낮을 때 재실행"은 그대로 둘 수 있다. | New |
| R-04 | Minor | services/u3-payment-service/src/main/java/kr/voiceparking/payment/payment/PaymentStore.java > locks / events / pgRequested | 잠금용 `Semaphore` 맵(`locks.computeIfAbsent(scope + ":" + id, ...)`)과 이벤트·pgRequested 집합에 제거 로직이 없다. 새 eventId, paymentId, 키마다 항목이 영구히 늘어난다. 인메모리 모의 서비스라 영향은 작지만, 서명 없이 열려 있는 `POST /api/payments`는 임의의 세션·키로 호출하면 Payment와 잠금이 계속 쌓인다. | 로컬 시연 범위의 한계임을 code-summary.md에 한 줄 적거나, 끝난 키·이벤트의 잠금을 비우는 정리를 넣는다. | New |
| R-05 | Minor | services/u3-payment-service/src/main/java/kr/voiceparking/payment/pg/MockPaymentGateway.java > register (61~73행) | `requested.add(paymentId)`를 `scheduler.schedule(...)`보다 먼저 한다. 스케줄러가 예외를 던지면(예: 종료 중 `TaskRejectedException`) 그 paymentId는 이미 등록된 것으로 남는다. PaymentService는 예외 때문에 `markPgRequested`를 못 하고, 같은 키로 다시 요청하면 `register`가 조용히 반환하므로 그 결제는 알림 없이 영구히 REQUESTED로 남는다. 예외 자체도 `settleRequest`에서 잡히지 않아 502가 아닌 500으로 나간다. | `schedule`이 성공한 뒤에 등록을 기록하거나, 실패 시 `requested`에서 제거한다. 그 경우를 다루는 시험을 하나 추가한다. | New |
| R-06 | Minor | services/u3-payment-service/src/main/java/kr/voiceparking/payment/signature/HmacSigner.java > 생성자 (25~30행) | 비밀 값은 null·공백만 거부하고 길이는 보지 않는다. `PAYMENT_WEBHOOK_SECRET=a`로도 기동하며 그 값이 웹훅의 유일한 방어선이다. 요구한 "기동 시 필수"는 충족한다. | 최소 길이(예: 32바이트)를 기동 시 검사하거나, u5 실행 스크립트가 충분히 긴 값을 만들어 주입한다고 code-summary.md에 적는다. | New |
| R-07 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u3-payment-service/code-generation/code-summary.md > 다른 유닛에 넘기는 사항 | 공유 계약 contract-summary.md는 `/api/payments`를 접두사로 적고 있고 이 서비스는 `/api/payments/webhook`을 브라우저에 노출하면 안 된다. 어긋남을 u4·u5에 "필수"로 넘기는 것으로 끝내고 계약 자체는 고치지 않았다. u4·u5가 계약 문구대로 접두사 프록시를 만들면 웹훅 경로가 열린다. 그 경우 서명 검증만 남는데, 위 비밀 길이 문제와 겹친다. | 이 불일치를 계약 수정 항목으로 사람이 처리하도록 표시하거나, u4·u5 단계에서 프록시 정확 일치를 확인하는 시험을 요구한다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `mvn -q -pl services/u3-payment-service test -Dtest='!PerformanceTest'` | 종료 코드 0, 실패·오류 출력 없음. 로그에 `StartupSecretTest`가 의도한 비밀 값 누락 기동 실패 스택이 남는다. | 기대한 223개 시험이 통과했다. 개수는 `-q` 출력에 나오지 않아 직접 확인하지 못했고 종료 코드로 판단했다. |
| `mvn -q -pl services/u3-payment-service verify -Dtest='!PerformanceTest'` | 종료 코드 0. `target/site/jacoco/`에 jacoco.xml·jacoco.csv 보고서 생성. | JaCoCo 라인 80% 하한(pom `coverage.line.minimum=0.80`, BUNDLE 규칙)을 `PerformanceTest` 제외 실행에서 통과했다. 94.3%라는 수치는 직접 재확인하지 않았다. |
| PerformanceTest | 실행하지 않음(지시에 따라) | 설계는 읽어서 판단했다. 통과 여부는 확인하지 못했으며 위 Minor 두 건에 반영했다. |

### Summary

웹훅 순서(크기 400 → 서명 401 → 형식 400 → 404 → 이벤트 잠금 → 중복 → Payment 잠금 → u2 호출 → 기록), 잠금 순서, 총 시간 제한, 원문 바이트 상수 시간 HMAC, 8KB 한정 읽기 필터, 패키지 import 규칙, 루프백 바인딩 기본값, 비밀 값 필수 기동은 코드와 시험에서 확인되어 Critical은 없다. 가장 무거운 우려는 u2가 새 예약을 돌려주는 같은 키 재요청에서 옛 Payment를 재사용해 알림이 영영 닿지 않는 경로이고, 그 밖은 추적성 표현, 성능 시험의 측정 방식, 소규모 견고성 사항이다.

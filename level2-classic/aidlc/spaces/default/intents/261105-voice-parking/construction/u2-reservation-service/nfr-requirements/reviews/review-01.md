## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-07T12:21:19Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-requirements/reliability-requirements.md > NFR5.5 및 performance-requirements.md > NFR1.4 | 예약 생성(C5)은 u3가 제한 시간 2초에 재시도 없이 부른다(contract-summary.md 제한 시간 표). 그런데 NFR5.5는 u2가 u1을 "제한 시간 2초와 200ms 간격 최대 2회 재시도"로 부르도록 해 실패 경로의 최악 소요가 약 6.4초(2초×3회+200ms×2)가 된다. NFR1.4의 "호출자의 제한 시간 2초 안에서 끝나야 한다"는 u1이 정상일 때만 적용된다. 따라서 u1이 느리면 u3가 먼저 502로 포기하고, u2는 그 뒤에 예약을 만들거나(고아 예약, 결제 대기 180초 타이머 가동) 세션 잠금(BR5.2)을 오래 쥘 수 있다. 어느 쪽 동작이 맞는지 요구사항이 정하지 않았다. | u1 호출 전체(재시도 포함)의 총 마감 시간을 u3의 2초보다 짧게(예: 1.5초 이내) 정하는 요구사항을 추가하거나, 포기한 호출 뒤 예약이 만들어질 수 있음을 인정하고 멱등 키 재요청으로 수습한다고 명시한다. 또 u1 대기 중 세션 잠금을 쥐는지(BR1.3 "한 번 검사 후 저장 직전 재검사"와의 관계)를 정하고 시험 기준에 총 소요 시간을 넣는다. | New |
| R-02 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-requirements/security-requirements.md > NFR6.1, NFR6.5, 위협 고려(위조된 결제 결과), Assumptions | `/internal/**`는 인증이 전혀 없고(NFR6.1 "어떤 비밀 값도 가지지 않는다") 방어는 "Vite 프록시에 연결하지 않는다"뿐이다. 그러나 프록시는 경계가 아니다. 포트 8082는 Spring Boot 기본값이면 모든 인터페이스에 열려 같은 네트워크의 누구나 `POST /internal/reservations/{id}/payment-result`에 APPROVED를 보내 결제 없이 예약을 CONFIRMED로 만들 수 있다. "호출자가 u3로 한정된다"는 [assumption]이며 시험 가능한 요구사항이 아니다. 같은 파일이 "열린 질문: 없음"이라고 쓰는 것도 이 가정과 어긋난다. | 서비스를 루프백(`server.address=127.0.0.1` 등)에만 바인딩하는 요구사항과 그 시험(외부 주소 접속 거부)을 NFR6.5에 추가한다. 또는 환경 변수로 주입하는 내부 호출용 공유 토큰을 두는 안과 비교해 선택을 기록한다. 이 가정을 사람이 확인하도록 열린 질문으로 올린다. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-requirements/tech-stack-decisions.md > NFR7.1 | 상위 NFR7과 team.md의 "병합 전 CI 실행. 하한은 약화할 수 없다"에서 CI 부분이 빠졌다. NFR7.1은 JaCoCo 80%와 빌드 실패만 말한다. Docker와 별도 배포 환경이 없는 이 프로젝트에서 "CI"가 무엇인지(로컬 스크립트, git 훅 등) 정의되어 있지 않다. | NFR7.1에 병합 전에 시험과 JaCoCo 검사를 실행하는 방법(예: u5-local-run의 검증 스크립트나 사전 병합 훅)을 명시한다. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-requirements/reliability-requirements.md > NFR5.3, observability-requirements.md > NFR5.10 | NFR5.3과 NFR5.10은 "중복 결과"도 거부 사실을 예약 ID, eventId, outcome, 현재 상태와 함께 로그에 남기라고 한다. 그런데 rules.md BR2.1 (2)는 같은 eventId가 이미 있으면 "처음 낸 판정을 그대로 돌려주고 상태도 푸시도 바꾸지 않는다"고만 하며 로그를 요구하지 않는다. 로그는 BR2.2(상태 기반 거부)에만 있다. | 중복 eventId 재응답 경로에도 로그를 남기는지 정해 NFR 문구와 기능 설계의 규칙을 일치시킨다. | New |
| R-05 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-requirements/tech-stack-decisions.md > 실시간 푸시, 타이머 행 | SSE 운영 조건이 NFR로 정해지지 않았다. (a) `SseEmitter` 기본 제한 시간 때문에 연결이 BR4.5의 15초 연결 유지 신호와 무관하게 끊길 수 있다. 제한 시간 설정 요구가 없다. (b) 스케줄러가 "단일 스레드 이상"으로 모호한데, 느린 클라이언트로의 블로킹 쓰기가 같은 스레드의 자동 취소를 늦춰 NFR2.1(±2초)을 깰 수 있다. NFR5.4는 쓰기 실패만 격리하고 쓰기 지연은 다루지 않는다. | `SseEmitter` 제한 시간과 연결 유지 15초 주기를 요구사항으로 넣고, 취소 타이머 스레드와 푸시 쓰기 스레드를 분리하거나 쓰기 시간 상한을 둔다고 명시하며 시험 기준을 추가한다. | New |
| R-06 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-requirements/performance-requirements.md > NFR1.1~NFR1.3, NFR2.2, NFR2.1 | "로컬 100회 반복한 값의 최댓값이 150ms 이하"는 JVM 예열과 GC 정지를 고려하지 않아 흔들리는 시험이 되기 쉽다. 측정 시작점(HTTP 수신, 컨트롤러 진입 등)과 예열 횟수도 정해지지 않았다. NFR2.1의 실제 타이머 시험은 180초가 걸려 TDD 반복과 병합 전 실행을 느리게 한다. | 예열 제외 횟수와 측정 구간(요청 필터 진입부터 `SseEmitter.send` 반환까지 등)을 명시하고, 180초 실시간 시험은 별도 표시해 병합 전 기본 실행에서 분리할 수 있게 한다. | New |
| R-07 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/nfr-requirements/traceability.json 및 observability-requirements.md, security-requirements.md | (1) NFR1.5(상태 확인 경로), NFR1.6~1.8(로그)은 1초 지연 목표인 NFR1이 아니라 운영 규칙에서 나왔는데 번호 규칙 때문에 NFR1 아래에 놓여 추적이 흐려진다. (2) 상위 NFR6은 웹훅 공유 비밀 값 검증인데 coverage는 `OK`로 묶였고, 실제로는 이 서비스에 해당 없는 부분(NFR6.1)과 새 입력 검증이 섞였다. (3) NFR4.3, NFR6.4, NFR5.8의 측정 방법이 "설계 확인", NFR9.1이 "코드 검토"로 시험 가능한 기준이 아니다. NFR6.2의 `passId`는 길이 상한이 없다. | NFR6 coverage에 웹훅 서명 검증은 u3 담당이라는 구분을 남기고, 가능한 항목은 자동 시험(예: 두 번째 인스턴스 기동 거부, 입력 길이 상한)으로 바꾼다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 수동 확인: requirements.md NFR1~NFR9 vs traceability.json upstream_ids | PASS | upstream_ids가 ["NFR1"~"NFR9"] 아홉 개를 모두 가지고 coverage 행도 아홉 개다. NFR3만 `N/A`(추천 경로는 u1 담당)이며 사유가 적혀 있다. |
| 수동 확인: coverage OK target이 산출 파일에 존재 | PASS | NFR1.1~1.8(performance 1.1~1.4, observability 1.5~1.8), NFR2.1~2.4(performance 2.1~2.2, reliability 2.3~2.4), NFR4.1~4.5, NFR5.1~5.10, NFR6.1~6.6, NFR7.1, NFR8.1~8.2, NFR9.1 모두 확인했다. |
| 수동 확인: 상위 번호 상속과 ID 유일성 | PASS | 모든 NFRx.y가 부모 번호 x를 가지며 파일 간 중복 ID가 없다. 다만 번호 상속이 의미 연결과 어긋나는 사례가 있다(Minor 마지막 행). |
| 수동 확인: 기술 선택 vs 팀 규칙 | PASS | Java 17, Maven, Docker 금지, TDD와 JaCoCo 80%, Java 별도 포매터 없음은 team.md 및 project.md와 충돌하지 않는다. CI 문구 누락만 별도 Minor다. |

### Summary

수치 목표(150ms, 180초 ± 2초)는 1초 예산과 일치하고 추적 구조는 건전하다. 다만 u1 호출의 재시도 시간이 상위 호출자의 2초 제한보다 길어지는 문제와 인증 없는 `/internal` 경로의 노출 경계가 요구사항 수준에서 정리되지 않았으므로 사람이 승인 전에 판단해야 한다.

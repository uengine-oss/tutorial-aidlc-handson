# 코드 요약: VoiceParking 프로토타입

## 만든 파일

모든 코드는 프로젝트 루트에 있습니다. 실행 방법과 시연 순서는 `RUN.md`, 기동 스크립트는 `run-all.sh`입니다.

| 영역 | 위치 | 내용 |
|---|---|---|
| 추천 서비스 (8081) | `backend/recommendation-service/` | 입력 해석(`QueryParser`), 종합 점수(`ScoreCalculator`), 추천 이유(`ExplanationGenerator`), 샘플 주차권 `sample-tickets.json`(강남역 7건, 홍대입구역 2건) |
| 예약 서비스 (8083) | `backend/reservation-service/` | 예약 상태(`PENDING`/`CONFIRMED`/`CANCELLED`), 180초 만료 스케줄러(`ExpiryScheduler`), SSE 알림(`SseHub`), 내부 확정 API |
| 결제 서비스 (8082) | `backend/payment-service/` | 결제 생성, 비동기 웹훅 처리(멱등·늦은 승인 무시), 모의 PG 시뮬레이터, 예약 서비스 호출 클라이언트 |
| 프런트엔드 | `frontend/` | Vue 3 + Vite. 입력창, 추천 카드 3개, 결제 대기·시연 버튼, 실패 후 재결제, 예약 완료 화면(SSE, 실패 시 폴링) |

## 주요 구현 결정

- 계획의 설계 결정을 그대로 따랐습니다: 서비스 3개, 서비스별 인메모리 저장소, 종합 점수 = 가격 40% + 거리 40% + 이용 시간 적합도 20%, 반경 1,000m 후보 풀, 정확히 180초는 취소.
- 시간 판단은 주입한 `Clock`으로 하므로 테스트가 실제로 기다리지 않습니다.
- 요청 검증(`@NotBlank`, `@Min`, `@Size`)을 각 서비스 입구에 추가해 잘못된 입력에 400과 메시지를 돌려줍니다.
- 서비스 간 호출이 실패하면 결제 서비스는 502를 돌려주고 로그에 남깁니다.
- 늦은 승인은 예약 서비스가 409로 거절하고, 결제 서비스는 웹훅에 200으로 답하며 `LATE_APPROVAL_IGNORED`로 기록합니다.
- 버전: Spring Boot 3.3.5, vue ^3.5, vite ^5.4, vitest ^2.1, jsdom ^25.

## 테스트 현황

| 명령 | 테스트 수 | 결과 |
|---|---|---|
| `mvn -q -f backend/pom.xml -pl recommendation-service -am test` | 18 | 통과 |
| `mvn -q -f backend/pom.xml -pl reservation-service -am test` | 19 | 통과 |
| `mvn -q -f backend/pom.xml -pl payment-service -am test` | 19 | 통과 |
| `npm --prefix frontend run test -- --run` | 16 | 통과 |

- NFR1(승인 웹훅 수신 후 1초 이내 완료 표시)은 서비스 수준과 MockMvc+SSE 수준에서 각각 검증했습니다.
- NFR2(180초 자동 취소)는 179초, 180초 정확히, 늦은 승인 경우를 가짜 시계로 검증했습니다.
- 결제 서비스 테스트 로그에 ERROR 한 줄이 나옵니다. 예약 서비스에 닿지 못하는 502 경우를 일부러 만든 것입니다.

## 변경 요청 반영: 결제 오류 응답 통일 (Step 15)

요청: 결제 관련 오류 응답을 모두 HTTP 402와 `{code, message}`로 통일하고, 앞으로의 결제 예외도 같은 규칙을 따른다. (R-01은 사용자가 브라우저로 직접 확인하기로 해서 테스트를 추가하지 않았습니다.)

- `payment-service`의 공통 예외 처리기가 모든 오류를 402 + `{code, message}`로 돌려줍니다. 코드: `PAYMENT_VALIDATION_FAILED`, `PAYMENT_NOT_FOUND`, `PAYMENT_ALREADY_APPROVED`, `RESERVATION_UNAVAILABLE`, `PAYMENT_INTERNAL_ERROR`. 내부 정보는 응답에 넣지 않고 로그에만 남깁니다.
- 프런트(`api.js`, `App.vue`, `PaymentPanel.vue`)가 `code`와 `message`를 읽어 결제 화면 안에 보여 주고, 실패 후 재결제 흐름은 그대로 유지됩니다.
- **규칙에서 뺀 것**: `result=FAILED` 웹훅(모의 PG의 결제 실패 통지)은 여전히 200이고, 실패 사유는 결제 상태 `FAILED`의 `failureReason`으로 조회합니다. 402로 바꾸면 웹훅 멱등 처리와 프런트의 상태 폴링이 깨지기 때문입니다.
- **범위가 넓은 점**: 모든 예외를 받는 처리기라서 없는 경로나 지원하지 않는 HTTP 메서드 같은 프레임워크 오류도 `PAYMENT_INTERNAL_ERROR`(402)가 됩니다.
- 늦은 승인(`LATE_APPROVAL_IGNORED`)은 오류 응답이 아니라 기록된 상태이므로 웹훅은 200입니다.

## 실행하지 않은 테스트와 검증

- 실제 브라우저에서 Vue 앱을 열어 본 적은 없습니다. 프런트는 단위 테스트와 빌드만 했고, `App.vue`의 SSE·결제 폴링 연결에는 자동 테스트가 없습니다.
- 1초 기준은 예약 서비스 안에서만 측정했습니다. 결제 서비스에서 예약 서비스로 가는 HTTP 구간을 포함한 측정은 하지 않았습니다.
- 손으로 실행한 흐름(예약 생성 → 결제 생성 → 모의 PG 승인 → 결제 `APPROVED`, 예약 `CONFIRMED`)은 한 번 확인했습니다. 그 시각 차이(약 1.4초)에는 확인용 대기 1.5초가 들어 있어 NFR1의 측정값이 아닙니다.

## 계획과 다른 점

- 체크박스를 파일 편집 도구가 아니라 `sed -i`로 표시했습니다. 바뀐 것은 `- [ ] Step`이 `- [x] Step`이 된 것뿐입니다.
- `frontend/dist/`가 한 번 빌드 확인으로 생겼습니다. 프로젝트의 `.gitignore`는 건드리지 않았고, `RUN.md`가 무시할 폴더를 안내합니다.

## 알려진 한계

- 웹훅에 서명 검증이 없습니다(프로토타입, `RUN.md`에 적음).
- 범위 밖: 실제 PG, 실제 주차장 데이터, 음성 인식, 계정, DB 영속화.

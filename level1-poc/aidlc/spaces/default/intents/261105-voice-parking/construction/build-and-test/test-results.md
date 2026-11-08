# 테스트 결과: VoiceParking 프로토타입

실행일: 2026-11-05. 명령은 `unit-test-instructions.md`(Code Generation)와 `build-instructions.md`에 적힌 그대로입니다.

## 빌드

| 명령 | 종료 코드 | 결과 |
|---|---|---|
| `mvn -q -f backend/pom.xml -DskipTests package` | 0 | 성공, jar 3개 생성 |
| `npm --prefix frontend run build` | 0 | 성공, 15개 모듈 변환, `dist/` 생성 |

## 단위·API 테스트

| 명령 | 총 | 통과 | 실패 | 건너뜀 |
|---|---|---|---|---|
| `mvn -f backend/pom.xml -pl recommendation-service -am test` | 18 | 18 | 0 | 0 |
| `mvn -f backend/pom.xml -pl payment-service -am test` | 19 | 19 | 0 | 0 |
| `mvn -f backend/pom.xml -pl reservation-service -am test` | 19 | 19 | 0 | 0 |
| `npm --prefix frontend run test -- --run` | 16 | 16 | 0 | 0 |
| **합계** | **72** | **72** | **0** | **0** |

세 Maven 명령은 모두 `BUILD SUCCESS`입니다. 결제 서비스 테스트 로그에 스택 트레이스가 한 번 나오는데, 예상치 못한 예외를 일부러 일으키는 테스트의 `log.error` 출력이며 실패가 아닙니다.

## 서비스 3개를 실제로 띄운 확인 (이 단계에서 추가로 실행)

빌드한 jar 3개를 띄우고(8081·8082·8083) Node 스크립트로 확인했습니다. 확인이 끝난 뒤 세 서비스를 모두 종료했습니다.

| 확인 | 결과 |
|---|---|
| 추천 호출 `강남역 근처 2시간 주차권 찾아줘` | 최적 후보에 종합 점수와 이유가 담겨 돌아옴 |
| 승인 웹훅을 보낸 시점부터 예약 서비스의 확정 이벤트(SSE)가 클라이언트에 도착하기까지, 10회 반복 | 5~114ms, 10회 모두 1초 이내 (처음 1회 114ms, 나머지 5~20ms). 결제 서비스 → 예약 서비스 HTTP 구간을 포함 |
| 모르는 결제 ID로 웹훅 | HTTP 402, `{"code":"PAYMENT_NOT_FOUND",...}` |
| 금액 0으로 결제 생성 | HTTP 402, `{"code":"PAYMENT_VALIDATION_FAILED",...}` |

## 커버리지

`poc` 범위는 새로 정한 커버리지 하한이 없고, 이 프로젝트에 커버리지 설정도 없습니다. 커버리지 수치는 측정하지 않았습니다. 요구사항별 테스트 존재 여부는 `cross-unit-traceability.md`에 있습니다.

## 실행하지 않은 것

- 실제 브라우저(Chromium 등)에서 Vue 화면을 여는 검증. 프런트 테스트는 jsdom(DOM 에뮬레이터)에서 돌았고 `App.vue`의 SSE·폴링 연결에는 자동 테스트가 없습니다. 사용자가 브라우저에서 직접 확인하기로 했습니다.
- 180초를 실제로 기다리는 검증. 180초 경계는 가짜 시계 테스트(179초, 정확히 180초, 늦은 승인)로 확인했습니다.

## Loop-Back Log

없음. 실패가 없어 되돌아간 적이 없습니다.

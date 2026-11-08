# VoiceParking 프로토타입 실행 안내

음성(텍스트) 입력으로 최적 주차권을 추천받고, 모의 결제(웹훅)를 거쳐 예약 완료까지 확인하는 프로토타입입니다.

## 준비물

- Java 17, Maven 3.9, Node 20 이상 (npm 포함)
- 외부 서비스·DB·API 키는 필요 없습니다. 저장소는 모두 메모리이고, PG는 모의입니다.

## 구성

| 구성 요소 | 포트 | 역할 |
|---|---|---|
| `backend/recommendation-service` | 8081 | 입력 해석, 점수 계산, 추천 3개와 이유 |
| `backend/payment-service` | 8082 | 결제 생성, 웹훅 처리, 모의 PG |
| `backend/reservation-service` | 8083 | 예약 상태, 180초 승인 마감, SSE 알림 |
| `frontend` | 5173 | Vue 3 화면 (Vite 프록시가 위 서비스로 전달) |

## 실행

```bash
bash run-all.sh
```

브라우저에서 http://localhost:5173 을 엽니다. 종료는 터미널에서 Ctrl+C 입니다.
수동으로 띄우려면 서비스마다 `mvn -f backend/pom.xml -pl <서비스> -am spring-boot:run` 을 실행하고, 프런트는 `npm --prefix frontend run dev` 입니다.

## 시연 순서

1. 입력창에 `강남역 근처 2시간 주차권 찾아줘`를 넣고 "찾기"를 누릅니다. 카드 3개가 나오고 1위에 "최적 추천"과 이유가 표시됩니다.
2. 원하는 카드의 "이 주차권으로 결제"를 누릅니다. 결제 대기 화면과 180초 카운트다운이 나옵니다.
3. "모의 PG: 실패"를 누르면 실패 사유와 "다시 결제하기"가 나옵니다. 다시 결제한 뒤 "모의 PG: 승인"을 누르면 1초 안에 "예약이 완료되었어요"가 나옵니다.
4. 결제하지 않고 180초를 넘기면 예약이 자동 취소되고, 이후 늦게 승인해도 확정되지 않습니다.
5. 후보가 부족한 경우를 보려면 `홍대입구역 2시간 주차`를 입력합니다(후보 2개와 안내 문구).

## 테스트 실행

```bash
mvn -q -f backend/pom.xml -pl recommendation-service -am test
mvn -q -f backend/pom.xml -pl payment-service -am test
mvn -q -f backend/pom.xml -pl reservation-service -am test
npm --prefix frontend install        # 처음 한 번
npm --prefix frontend run test -- --run
```

## 동작 규칙 요약

- 점수: 가격 x 0.4 + 거리 x 0.4 + 이용 시간 적합도 x 0.2. 가격·거리는 후보 중 최솟값 대비 비율, 적합도는 1시간 어긋날 때마다 50점 감점. 동점이면 가격이 낮은 쪽.
- 후보 풀: 목적지 반경 1,000m 이내. 샘플 데이터는 `backend/recommendation-service/src/main/resources/sample-tickets.json` 입니다.
- 예약 상태: `PENDING` -> `CONFIRMED` 또는 `CANCELLED`. 정확히 180초가 되는 순간도 취소로 봅니다.
- 늦은 승인: 취소된 예약에 승인이 오면 예약 서비스는 409를 돌려주고, 결제는 `LATE_APPROVAL_IGNORED`로 기록됩니다(웹훅 자체는 200 응답).
- 중복 웹훅은 한 번만 처리합니다. 예약 서비스에 닿지 못해도 결제는 `PENDING`으로 유지됩니다.
- **결제 오류 응답 규칙**: 결제 서비스(`payment-service`)의 결제 실패·오류 응답은 모두 HTTP `402 Payment Required` + `{ "code": "...", "message": "..." }` 입니다. 앞으로 추가하는 결제 관련 예외도 `ApiExceptionHandler`에서 같은 규칙을 따릅니다. 코드: `PAYMENT_VALIDATION_FAILED`(입력 검증), `PAYMENT_NOT_FOUND`(모르는 결제 ID), `PAYMENT_ALREADY_APPROVED`(이미 승인된 예약), `RESERVATION_UNAVAILABLE`(예약 서비스 연결 실패), `PAYMENT_INTERNAL_ERROR`(그 밖의 예외). 결제 실패 사유(예: 모의 PG 실패)는 결제 상태 `FAILED`의 `failureReason`으로 조회합니다. 프런트는 `code`와 `message`를 읽어 결제 화면에 사유를 보여 줍니다. 예약·추천 서비스의 오류 형식은 그대로입니다.

## 알아 둘 점

- 프로토타입이라 웹훅에 서명 검증이 없습니다. 실제 PG를 붙일 때는 서명 검증을 반드시 추가하세요.
- 빌드 산출물(`backend/*/target/`, `frontend/node_modules/`, `frontend/dist/`)은 저장소에 올리지 않도록 `.gitignore`에 추가하는 것을 권장합니다(기존 `.gitignore`는 수정하지 않았습니다).
- 실제 음성 인식은 브라우저가 Web Speech API를 지원할 때만 "말하기" 버튼이 나타납니다. 지원하지 않아도 글자 입력으로 모두 시연할 수 있습니다.

# u1-search-service 비즈니스 규칙

## Sources

- [scope] Workflow-selected scope: classic
- [Q1] 종합 점수 = 가격 40% + 거리 40% + 시간 적합도 20% (requirements-analysis Q1)
- [Q5] 가격·거리는 후보 간 min-max 0~100 환산, 시간 적합도는 요청 길이에 가까울수록 높음, 포함하지 못하면 제외 (requirements-analysis Q5)
- [memory:M1] requirements.md(FR1~FR3, NFR3), stories.md(US1.1, US2.1, US2.2), contract-summary.md(C1, C4)

```yaml
rules:
  - id: BR1.1
    statement: "입력 문장에서 등록된 장소 이름을 찾아 장소로 추출한다."
    category: validation
    applies_to: ParsedQuery.placeName
    trigger: "추천 요청을 받을 때"
    logic: "IF 문장에 ParkingLot.placeName에 등록된 이름이 들어 있으면 THEN 그 이름을 장소로 쓴다. 이름이 둘 이상이면 문장에서 먼저 나오는 것을 쓴다."
    violation: "장소를 찾지 못하면 BR1.3"
    source: [FR1.1]
  - id: BR1.2
    statement: "입력 문장에서 이용 시간을 시간 단위 정수로 추출한다."
    category: validation
    applies_to: ParsedQuery.durationHours
    trigger: "추천 요청을 받을 때"
    logic: "IF 문장에 '숫자 + 시간' 또는 '한/두/세/네/다섯 시간' 표현이 있으면 THEN 해당 정수(1~24)를 이용 시간으로 쓴다. 표현이 둘 이상이면 먼저 나오는 것을 쓴다."
    violation: "이용 시간을 찾지 못하거나 1~24 밖이면 BR1.4"
    source: [FR1.1]
  - id: BR1.3
    statement: "장소가 없으면 추천하지 않고 장소가 필요하다고 안내한다."
    category: validation
    applies_to: 추천 요청
    trigger: "BR1.1에서 장소를 찾지 못함"
    logic: "IF 장소가 없으면 THEN 422 MISSING_PLACE(장소를 알려 주세요)를 돌려주고 추천을 진행하지 않는다."
    violation: "해당 없음 (이 규칙이 오류 처리다)"
    source: [FR1.2]
  - id: BR1.4
    statement: "이용 시간이 없으면 추천하지 않고 이용 시간이 필요하다고 안내한다."
    category: validation
    applies_to: 추천 요청
    trigger: "BR1.2에서 이용 시간을 찾지 못함"
    logic: "IF 장소는 있고 이용 시간이 없으면 THEN 422 MISSING_DURATION(이용 시간을 알려 주세요)을 돌려준다. 둘 다 없으면 BR1.3이 먼저 적용된다."
    violation: "해당 없음"
    source: [FR1.2]
  - id: BR1.5
    statement: "음성 인식은 하지 않고 이미 텍스트인 입력만 받는다."
    category: constraint
    applies_to: 추천 요청
    trigger: "항상"
    logic: "IF 본문 text가 비어 있거나 200자를 넘으면 THEN 400 INVALID_TEXT를 돌려준다."
    violation: "400 INVALID_TEXT"
    source: [FR1.3]
  - id: BR1.6
    statement: "X-Session-Id 헤더는 필수로 받지만 이 서비스는 저장하거나 쓰지 않는다."
    category: constraint
    applies_to: C1 추천 요청
    trigger: "항상"
    logic: "IF X-Session-Id가 UUID 형식이 아니면 THEN 400 MISSING_SESSION을 돌려준다. 그 외에는 값을 쓰지 않는다. 같은 세션의 예약만 조회하는 규칙은 u2-reservation-service가 맡는다."
    violation: "400 MISSING_SESSION"
    source: [FR8.1]
  - id: BR2.1
    statement: "요청 장소와 같은 장소 이름을 가진 주차장의 주차권을 후보로 모은다."
    category: policy
    applies_to: ParkingPass
    trigger: "ParsedQuery가 만들어짐"
    logic: "IF ParkingLot.placeName이 ParsedQuery.placeName과 같으면 THEN 그 주차장의 모든 주차권을 후보로 모은다."
    violation: "해당 없음"
    source: [FR2.1]
  - id: BR2.2
    statement: "한 번에 이용할 수 있는 최대 시간이 요청 이용 시간보다 짧은 주차권은 후보에서 제외한다."
    category: constraint
    applies_to: ParkingPass
    trigger: "후보를 모은 뒤"
    logic: "IF ParkingPass.availableHours < ParsedQuery.durationHours THEN 후보에서 뺀다."
    violation: "해당 없음"
    source: [FR2.2]
  - id: BR2.3
    statement: "후보가 하나도 없으면 빈 추천 목록을 돌려준다."
    category: policy
    applies_to: 추천 응답
    trigger: "BR2.2 적용 뒤 후보가 0개"
    logic: "IF 후보가 0개이면 THEN 200 응답에 recommendations를 빈 배열로 돌려준다 (오류가 아니다)."
    violation: "해당 없음"
    source: [FR2.3]
  - id: BR3.1
    statement: "후보마다 가격 점수와 거리 점수와 시간 적합도 점수를 후보 간 min-max로 0~100점으로 환산한다."
    category: calculation
    applies_to: ScoredPass
    trigger: "후보가 1개 이상일 때"
    logic: "값 v를 후보 전체의 최솟값 min과 최댓값 max로 환산한다. score = 100 x (max - v) / (max - min). 낮을수록 고득점이다. max와 min이 같으면(후보 1개이거나 값이 모두 같으면) 그 항목은 모두 100점이다. 가격은 totalPrice, 거리는 distanceMeters, 시간 적합도는 diff = availableHours - durationHours(0 이상)를 v로 쓴다."
    violation: "해당 없음"
    source: [FR3.2, FR3.3]
  - id: BR3.2
    statement: "종합 점수는 가격 0.4, 거리 0.4, 시간 적합도 0.2의 가중합이며 소수점 아래는 반올림한다."
    category: calculation
    applies_to: ScoredPass.totalScore
    trigger: "BR3.1 이후"
    logic: "totalScore = round(0.4 x priceScore + 0.4 x distanceScore + 0.2 x timeFitScore). 반올림은 0.5에서 올린다. 환산 점수는 반올림하지 않은 값으로 계산한다."
    violation: "해당 없음"
    source: [FR3.1]
  - id: BR3.3
    statement: "종합 점수가 높은 순으로 정렬하고 동점이면 가격이 낮은 쪽을 앞세운다."
    category: policy
    applies_to: 추천 순서
    trigger: "BR3.2 이후"
    logic: "정렬 키 순서: totalScore 내림차순, totalPrice 오름차순, distanceMeters 오름차순, passId 오름차순. 마지막 키는 결과를 항상 같게 하려는 것이다."
    violation: "해당 없음"
    source: [FR3.6]
  - id: BR3.4
    statement: "최적 1개와 비교 후보 2개만 돌려준다. 후보가 3개 미만이면 있는 만큼만 돌려준다."
    category: policy
    applies_to: 추천 응답
    trigger: "BR3.3 이후"
    logic: "정렬한 첫째에 rank BEST, 둘째와 셋째에 rank CANDIDATE를 붙이고 나머지는 응답에 넣지 않는다."
    violation: "해당 없음"
    source: [FR3.4]
  - id: BR3.5
    statement: "추천 이유는 종합 점수에 가장 크게 기여한 항목의 이름을 반드시 포함한다."
    category: policy
    applies_to: Recommendation.reason
    trigger: "BR3.4 이후"
    logic: "기여도 = 가중치 x 항목 점수. 기여도가 가장 큰 항목이 topFactor(PRICE, DISTANCE, TIME_FIT)다. 같으면 PRICE, DISTANCE, TIME_FIT 순으로 앞선 것을 쓴다. text는 '{주차장 이름}: {항목 이름(가격, 거리, 시간 적합도)}이(가) 가장 큰 강점입니다 (가격 {p}점, 거리 {d}점, 시간 적합도 {t}점)' 형식이며 점수는 정수로 반올림해 보여 준다."
    violation: "해당 없음"
    source: [FR3.5]
  - id: BR3.6
    statement: "음성 텍스트를 받은 시점부터 추천 결과를 돌려주기까지 2초 이내다."
    category: constraint
    applies_to: 추천 요청
    trigger: "항상"
    logic: "목업 데이터를 메모리에서 읽어 계산하므로 로컬 환경에서 2초 이내여야 한다. 시험에서 측정한다."
    violation: "시험 실패"
    source: [NFR3]
  - id: BR4.1
    statement: "추천 응답의 가격과 예약 생성용 가격은 같은 계산을 쓴다."
    category: calculation
    applies_to: price, totalPrice
    trigger: "추천 응답을 만들 때와 주차권 조회(C4)를 받을 때"
    logic: "price = hourlyPrice x durationHours. 추천 응답의 price와 C4 응답의 price는 같은 durationHours에 대해 항상 같다."
    violation: "해당 없음"
    source: [FR3.1, FR4.1]
  - id: BR4.2
    statement: "주차권 조회(C4)는 없는 주차권과 시간을 제공하지 못하는 주차권을 구분해 알린다."
    category: validation
    applies_to: GET /internal/passes/{passId}
    trigger: "U2가 예약 생성을 위해 조회할 때"
    logic: "IF passId가 없으면 THEN 404 PASS_NOT_FOUND. IF durationHours가 없거나 1 미만이면 THEN 400 INVALID_DURATION. IF availableHours < durationHours THEN 422 DURATION_NOT_AVAILABLE. 그 외에는 passId, lotName, durationHours, price를 돌려준다."
    violation: "각 오류 응답"
    source: [FR4.2]
```

## 요약

| 규칙 | 한 줄 요약 |
|---|---|
| BR1.1~BR1.6 | 입력 해석(장소, 시간, 부족 안내, 입력 형식, 세션 헤더) |
| BR2.1~BR2.3 | 후보 모으기, 시간 못 채우는 주차권 제외, 후보 없음 |
| BR3.1~BR3.6 | 점수 환산과 가중합, 정렬과 동점 규칙, 3개 선택, 추천 이유, 2초 목표 |
| BR4.1~BR4.2 | 가격 계산 일치, 주차권 조회 오류 구분 |

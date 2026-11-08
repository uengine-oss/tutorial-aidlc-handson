# u1-search-service 엔티티

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] unit-of-work.md(U1), components.md(Recommendation의 ParkingLot, ParkingPass), contract-summary.md(C1, C4), requirements.md(FR2, FR3), stories.md(US2.1, US2.2)

이 서비스는 목업 주차장·주차권 데이터만 소유하고 읽기 전용이다. 음성 텍스트 해석 결과(장소, 이용 시간)는 저장하지 않는 값(ParsedQuery)이다.

```yaml
entities:
  - name: ParkingLot
    description: 요청 장소 주변에 있는 목업 주차장. 하나의 장소 이름과 그 장소로부터의 거리를 가진다.
    attributes:
      - { name: lotId, type: string, required: true, unique: true, constraints: "영문 소문자·숫자·하이픈" }
      - { name: name, type: string, required: true, constraints: "1~50자" }
      - { name: placeName, type: string, required: true, constraints: "요청 장소로 쓰일 수 있는 이름 (예: 강남역)" }
      - { name: distanceMeters, type: integer, required: true, min: 0, constraints: "placeName에서 주차장까지의 직선거리(m)" }
    constraints:
      - "lotId는 전체에서 유일하다"
    relationships:
      - { to: ParkingPass, cardinality: "1..*", direction: "ParkingLot -> ParkingPass", note: "하나의 주차장은 하나 이상의 주차권을 가진다" }

  - name: ParkingPass
    description: 주차장이 파는 주차권 한 종류. 시간당 요금과 한 번에 쓸 수 있는 최대 시간을 가진다.
    attributes:
      - { name: passId, type: string, required: true, unique: true, constraints: "영문 소문자·숫자·하이픈" }
      - { name: lotId, type: string, required: true, references: ParkingLot.lotId }
      - { name: hourlyPrice, type: integer, required: true, min: 1, constraints: "시간당 요금(원)" }
      - { name: availableHours, type: integer, required: true, min: 1, constraints: "한 번에 이용할 수 있는 최대 시간(시간)" }
    constraints:
      - "passId는 전체에서 유일하다"
      - "lotId는 존재하는 ParkingLot를 가리킨다"

  - name: ParsedQuery
    description: 음성 텍스트에서 추출한 검색 조건. 저장하지 않고 요청 처리 중에만 존재하는 값이다.
    attributes:
      - { name: placeName, type: string, required: true, constraints: "ParkingLot.placeName에 등록된 이름" }
      - { name: durationHours, type: integer, required: true, min: 1, max: 24 }

  - name: ScoredPass
    description: 후보 한 건과 그 점수. 저장하지 않고 요청 처리 중에만 존재하는 값이다.
    attributes:
      - { name: passId, type: string, required: true }
      - { name: totalPrice, type: integer, required: true, constraints: "hourlyPrice x durationHours" }
      - { name: priceScore, type: number, required: true, min: 0, max: 100 }
      - { name: distanceScore, type: number, required: true, min: 0, max: 100 }
      - { name: timeFitScore, type: number, required: true, min: 0, max: 100 }
      - { name: totalScore, type: integer, required: true, min: 0, max: 100 }
      - { name: topFactor, type: string, required: true, allowed_values: [PRICE, DISTANCE, TIME_FIT] }
```

## 요약

| 엔티티 | 저장 | 소유 | 비고 |
|---|---|---|---|
| ParkingLot | 시작 시 적재한 목업 | u1-search-service | 읽기 전용 |
| ParkingPass | 시작 시 적재한 목업 | u1-search-service | 읽기 전용 |
| ParsedQuery | 저장 안 함 | 요청 처리 중 | 값 객체 |
| ScoredPass | 저장 안 함 | 요청 처리 중 | 값 객체 |

## 목업 데이터

시작할 때 아래 데이터를 적재한다. 단계별 시험이 같은 값으로 기대 순위를 계산한다.

| lotId | name | placeName | distanceMeters | passId | hourlyPrice | availableHours |
|---|---|---|---|---|---|---|
| gangnam-public | 강남역 공영주차장 | 강남역 | 120 | gangnam-public-1 | 3000 | 3 |
| yeoksam-bldg | 역삼 빌딩 주차장 | 강남역 | 80 | yeoksam-bldg-1 | 3500 | 2 |
| teheran | 테헤란로 주차장 | 강남역 | 300 | teheran-1 | 2500 | 4 |
| gangnam-private | 강남 민영 주차장 | 강남역 | 200 | gangnam-private-1 | 2000 | 1 |
| sinnonhyeon | 신논현 주차장 | 강남역 | 450 | sinnonhyeon-1 | 1800 | 6 |
| hongdae-public | 홍대입구역 공영주차장 | 홍대입구역 | 100 | hongdae-public-1 | 2800 | 4 |
| hongdae-bldg | 홍대 빌딩 주차장 | 홍대입구역 | 250 | hongdae-bldg-1 | 2200 | 3 |

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-05T09:53:35Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u1-search-service/functional-design/rules.md > BR1.5, BR1.6, BR4.2 (contract-summary.md의 C1, C4 응답 목록과 대조) | 규칙이 계약에 없는 응답을 추가한다. C1은 200/422만 정의하는데 BR1.5는 400 INVALID_TEXT, BR1.6은 400 MISSING_SESSION을 돌려준다. C4는 200/404/422만 정의하는데 BR4.2는 400 INVALID_DURATION을 추가한다. 같은 코드 MISSING_SESSION을 C3 SSE는 401로 정의해 서비스 간 상태 코드도 어긋난다. C4 소비자인 U2는 계약에 없는 400을 처리할 근거가 없다. | 계약 소유자(U1)로서 contract-summary.md의 C1에 400 INVALID_TEXT, 400 MISSING_SESSION을, C4에 400 INVALID_DURATION을 추가하고 U2·U4와 합의한다. MISSING_SESSION의 상태 코드(400 또는 401)를 C1과 C3에서 하나로 맞춘다. | New |
| R-02 | Major | aidlc/spaces/default/intents/261105-voice-parking/construction/u1-search-service/functional-design/traceability.json > AC2.1.1, AC2.2.2 (entities.md 목업 데이터와 대조) | AC2.1.1은 강남역에 2시간을 포함하는 주차권이 5개 있어 2시간 요청에 5개가 조회된다고 하고, AC2.2.2는 후보가 5개일 때를 전제한다. 그런데 목업 데이터는 강남역 주차권 5개 중 gangnam-private-1(최대 1시간)이 2시간 요청에서 빠지므로 대표 요청의 후보는 4개다(functional-spec.md 계산 예시가 이를 보여 준다). 두 AC가 같은 데이터에서 동시에 성립하지 않는데 traceability는 OK로 표시했다. 이대로면 시험이 기대하는 후보 수와 순위 기준이 정해지지 않는다. | 5개 후보를 재현할 요청(예: 1시간 요청은 5개 모두 통과)을 명시하거나 AC 해석을 고쳐 적는다. AC2.1.3(후보 없음), AC2.2.3(후보 2개), AC2.2.4(동점)를 어느 요청 또는 별도 시험 데이터로 재현하는지도 spec에 적는다. | New |
| R-03 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u1-search-service/functional-design/entities.md > ParkingPass 속성 (components.md의 ParkingPass와 대조) | Domain Design의 ParkingPass는 `passId, price, availableFrom, availableUntil`인데 entities.md는 `hourlyPrice, availableHours, lotId`로 바꿨다. 가정에 요금 계산 방식은 적혀 있지만 상위 모델의 속성을 바꾼다는 점은 어디에도 적혀 있지 않다. | 엔티티 설명 또는 Assumptions에 상위 속성에서 이렇게 바뀐 이유를 적고, 필요하면 components.md의 엔티티 표도 맞춘다. | New |
| R-04 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u1-search-service/functional-design/functional-spec.md > W2 1~2단계 vs rules.md > BR4.2 | W2는 durationHours 검사(400)를 먼저 하고 passId 검사(404)를 그다음에 하는데, BR4.2의 logic은 404를 먼저 나열한다. passId도 없고 durationHours도 잘못된 요청의 응답이 문서마다 다르다. 또한 `abc` 같은 숫자가 아닌 값은 '없거나 1 미만' 문구에 해당하지 않는다. | 검증 순서를 하나로 정해 spec과 rules를 같게 쓰고, 숫자가 아닌 값도 400 INVALID_DURATION에 포함한다고 적는다. | New |
| R-05 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u1-search-service/functional-design/rules.md > BR1.2, BR1.4 | 이용 시간이 0시간이거나 24시간을 넘으면 MISSING_DURATION(이용 시간을 알려 주세요)이 된다. 사용자가 시간을 말했는데도 "알려 주세요"라는 안내가 나가 오해를 부른다. | 범위 밖 값에 별도 안내 문구를 쓰거나, 같은 코드를 쓰되 메시지에 허용 범위(1~24시간)를 담는다고 명시한다. | New |
| R-06 | Minor | aidlc/spaces/default/intents/261105-voice-parking/construction/u1-search-service/functional-design/rules.md > BR3.1, BR3.2, functional-spec.md > W1 8단계 | 응답 필드가 ScoredPass 값에서 어떻게 채워지는지 한 곳에 적혀 있지 않다(query.place는 ParsedQuery.placeName, score는 totalScore, price는 totalPrice, lotName과 distanceMeters는 ParkingLot에서, rank는 정렬 순서에서). 또 BR3.2의 "0.5에서 올림"은 부동소수점으로 계산하면 경계에서 어긋날 수 있다. | W1에 C1 응답 필드 매핑 표를 한 줄씩 추가한다. 점수 계산을 정수 비율이나 고정 소수점으로 하라는 문구를 BR3.2에 보탠다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 계산 예시 재계산 (손 계산, 강남역 2시간, 후보 4건) | PASS | 총액 6000/7000/5000/3600, 가격 점수 29.41/0/58.82/100, 거리 점수 89.19/100/40.54/0, 시간 적합도 75/100/50/0이다. 종합은 62.44→62, 60, 49.75→50, 40으로 spec 표와 같다. 최적 gangnam-public-1의 기여도 11.76/35.68/15.0은 spec의 11.8/35.7/15.0과 맞고 topFactor는 DISTANCE다. |
| AC2.2.1 공식 재현 | PASS | 100×0.4+80×0.4+100×0.2=92, 50×0.4+100×0.4+60×0.2=72, 0은 0이다. BR3.2 공식으로 재현된다. |
| 이야기 AC ↔ traceability 매핑 | PARTIAL | AC1.1.1~AC2.2.6이 모두 규칙에 연결된다. 다만 AC2.1.1과 AC2.2.2의 후보 5개 전제가 목업 데이터와 맞지 않는다(Major 2). |
| 프레임워크 코드 포함 여부(grep) | PASS | spec, entities, rules에 프레임워크 전용 코드나 어노테이션이 없다. |
| 엔티티 yaml ↔ Domain Design·목업 데이터 | PASS with note | lotId 참조와 목업 7건이 모두 일관된다. 상위 모델과 속성 차이가 있다(Minor 1). |

### Summary

계산 예시와 AC2.2.1은 BR3.1~BR3.5로 정확히 재현되고 프레임워크 코드도 없다. 승인 전에 두 가지를 확인하면 된다. 하나는 규칙이 추가한 400 응답을 계약 C1·C4에 반영하는 것이고, 다른 하나는 AC2.1.1·AC2.2.2의 후보 5개 전제를 목업 데이터와 맞추는 것이다.

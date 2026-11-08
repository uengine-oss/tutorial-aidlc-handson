## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-05T10:36:09Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | services/u1-search-service/src/main/java/kr/voiceparking/search/service/QueryParser.java > DURATION 정규식 `(\d+\|한\|두\|세\|네\|다섯)\s*시간` | 단어 경계가 없어 규칙에 없는 한글 수사가 조용히 잘못 해석된다. "열두 시간"은 "두 시간"(2시간)으로, "스물네 시간"은 "네 시간"(4시간)으로, "열한 시간"은 "한 시간"(1시간)으로 읽힌다. 오류 없이 엉뚱한 이용 시간으로 추천과 가격이 나간다. BR1.2는 한/두/세/네/다섯만 정의하므로 규칙 위반은 아니지만, 음성 입력에서 흔한 표현이다. | 수사 앞에 한글이 붙어 있으면 일치하지 않도록 앞뒤 경계(예: 앞에 한글 음절이 없을 것)를 두거나, 지원하지 않는 수사는 MISSING_DURATION으로 처리한다. 해당 문장 3개를 QueryParserTest에 추가한다. | New |
| R-02 | Minor | services/u1-search-service/src/main/java/kr/voiceparking/search/service/PassLookupService.java > lookup() 검증 순서, 그리고 inception/contract-design/contract-summary.md > C4 | 구현은 durationHours를 먼저 검사해 범위(1~24) 밖이면 400 INVALID_DURATION을 낸다. 그래서 없는 passId라도 duration이 잘못되면 404 대신 400이 나간다. C4 계약에는 404와 422만 있고 400과 상한 24는 적혀 있지 않다(minimum 1만). rules.md BR4.2는 404를 먼저 나열하고 상한 24는 말하지 않는다. 소비자(U2)가 400을 처리해야 하는지 계약만으로는 알 수 없다. | C4 계약에 400 INVALID_DURATION과 상한 24를 추가하거나, 구현을 계약에 맞춰 순서와 상한을 조정한다. 어느 쪽이든 U2가 보게 될 응답을 한 곳에 명시한다. | New |
| R-03 | Minor | services/u1-search-service/src/main/java/kr/voiceparking/search/web/SearchController.java > recommend() 의 text 길이 검사 | `text.length()`는 UTF-16 코드 유닛 수라서 이모지 같은 보충 문자는 2로 센다. 200자 상한(BR1.5)이 사용자가 보는 글자 수와 어긋날 수 있다. 기능상 위험은 낮다. | `codePointCount`로 세거나, 코드 유닛 기준임을 주석으로 남긴다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `mvn -q -pl services/u1-search-service verify` | 실패 출력 없이 종료. surefire 보고서 12개 클래스, 65개 테스트, 실패 0, 오류 0, 건너뜀 0 | 테스트와 JaCoCo 80% 라인 게이트 통과 |
| jacoco.csv 합계 | 라인 300 커버, 13 미커버 = 95.8% | 80% 하한을 크게 넘음. pom.xml의 `coverage.line.minimum`은 0.80, 요소는 BUNDLE, 카운터는 LINE이며 exclude 항목이 없다. 하한을 낮추거나 패키지를 빼서 부풀린 흔적은 없다. |
| 점수 계산 대조 | RecommendationServiceTest가 62/60/50/40 순서와 점수, 1위의 29/89/75와 DISTANCE, AC2.2.1의 92/72/0과 50/100/60, 동점 시 낮은 가격 우선, 전체 동점 시 passId 순, 단일 후보 100점을 정확한 값으로 단언한다. | RecommendationService와 Ratio는 BR3.1~BR3.5와 일치한다. min-max 환산과 반올림 전 합산을 정수 분수(`Ratio`)로 하고 `(2*num+den)/(2*den)`로 0.5에서 올려 BR3.2를 지킨다. 정렬 키 순서는 BR3.3과 같다. 단언이 느슨한 속성 검사가 아니라 정확한 숫자다. |
| C1/C4 계약 대조 | SearchResponse는 query{place,durationHours}, recommendations[]{passId,lotName,price,distanceMeters,score,rank,reason{topFactor,text}}로 C1과 같다. 상태 코드는 200/400/422, C4는 200/404/422(+400)이고, 오류 본문은 {code,message}다. | 필드 이름과 상태 코드는 계약과 맞다. C4의 400 추가는 위 Minor 2번 참고. |
| 입력 에코 | ApiError 메시지는 ErrorCode의 고정 문구이고, 프레임워크 오류도 고정 문구다. RequestLogFilter는 본문을 기록하지 않고 세션 ID는 UUID 형식일 때만 앞 8자를 남기며, 경로의 제어 문자를 지운다. query.place는 사용자 문장이 아니라 등록된 장소 이름이다. | 사용자 텍스트가 응답이나 로그로 새지 않는다. |
| 비밀·Docker·스텁 | services 아래 TODO/FIXME 없음, Dockerfile/docker-compose 없음, application.properties에 비밀 없음(포트만 환경 변수), actuator는 health만 노출하고 NoSecretsTest/HealthEndpointTest가 env/beans 404를 확인한다. | 프로젝트 규칙(NEVER Docker, 비밀 금지, 스텁 금지)에 맞다. |
| traceability.json / source-manifest.json | 표본으로 확인한 target 경로(QueryParser, Ratio, RecommendationService, PassLookupService, 각 테스트 클래스, application.properties 등)가 모두 존재한다. N/A 항목 AC1.1.4, NFR2.4는 다른 유닛 소관이라는 사유가 적혀 있다. manifest는 루트 pom.xml, 서비스 pom.xml, src/main/, src/test/를 포괄한다. | 추적 정보와 소스 목록이 코드와 일치한다. |

### Summary

계획대로 구현되었고, 점수 계산은 BR3.1~BR3.5와 기능 명세 예시(62/60/50/40, 92/72/0)를 정확한 숫자로 재현하며 65개 테스트와 95.8% 라인 커버리지로 80% 게이트를 통과한다. 남은 것은 모두 Minor이고, 가장 눈에 띄는 것은 "열두 시간"이 2시간으로 조용히 잘못 해석되는 정규식 경계 문제와 C4 계약에 없는 400/상한 24 동작이다.

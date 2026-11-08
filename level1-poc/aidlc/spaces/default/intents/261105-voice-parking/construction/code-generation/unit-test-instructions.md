# 단위 테스트 안내: VoiceParking 프로토타입

## 사전 준비

- Java 17, Maven 3.9, Node 20 이상(이 환경: Node 25, npm 11). 그 밖의 수동 준비는 필요 없습니다.
- 처음 한 번 `npm --prefix frontend install`로 프런트 의존성을 설치합니다.

## 테스트 프레임워크

- 백엔드: JUnit 5 + Spring Boot Test(MockMvc), 서비스마다 `src/test/java`.
- 프런트: vitest + @vue/test-utils(jsdom).

## 실행 명령 (서비스별, 범위를 좁힘)

```bash
mvn -q -f backend/pom.xml -pl recommendation-service -am test
mvn -q -f backend/pom.xml -pl payment-service -am test
mvn -q -f backend/pom.xml -pl reservation-service -am test
npm --prefix frontend run test -- --run
```

프로젝트 전체를 한 번에 도는 명령(`mvn test` 단독, `npm test` 단독)은 쓰지 않습니다.

## 테스트 구성 (Minimal 전략: 요구사항당 1개 이상, 컴포넌트당 정상 경로 1개 이상, 약 20개)

| 서비스 | 대상 | 확인 내용 | 요구사항 |
|---|---|---|---|
| recommendation | 입력 해석기 | "강남역 근처 2시간 주차권 찾아줘" → 목적지 강남역, 2시간 | FR1.1, FR1.2 |
| recommendation | 입력 해석기 | 목적지·시간이 없는 문장은 재입력 안내 | FR1.2 (경계) |
| recommendation | 점수 계산기 | 40/40/20 비중으로 기대 점수·1위가 나옴, 같은 입력은 같은 결과 | FR2.1 |
| recommendation | 점수 계산기 | 동점이면 가격이 낮은 쪽이 1위 | FR2.1 (경계) |
| recommendation | 추천 API | 후보 3개(최적 1 + 비교 2), 최적에 설명 포함 | FR2.2, FR2.3 |
| recommendation | 추천 API | 후보가 3개 미만이면 있는 만큼과 안내 문구 | FR2.2 (경계) |
| reservation | 상태 전이 | `PENDING`→`CONFIRMED`, 취소 후 확정은 거절(409) | FR4.1, FR3.4 |
| reservation | 만료 스케줄러 | 마감(180초)이 지난 예약은 `CANCELLED`, 정확히 180초도 취소 | FR3.4, NFR2 |
| reservation | SSE | 확정 이벤트가 웹훅 처리 후 1초 이내에 도착 | FR4.2, NFR1 |
| payment | 웹훅 처리 | 승인 웹훅 → 결제 승인 + 예약 확정 요청 | FR3.2 |
| payment | 웹훅 처리 | 중복 웹훅은 한 번만 처리, 모르는 결제 ID는 404 | FR3.2 (경계) |
| payment | 늦은 승인 | 취소된 예약에 승인이 오면 `LATE_APPROVAL_IGNORED` | FR3.2, FR3.4 |
| payment | 실패 | 실패 웹훅 → 실패 사유와 재결제 가능 상태 | FR3.3 |
| frontend | 추천 화면 | 카드 3개, 최적 강조와 이유 표시 | FR2.2, FR2.3 |
| frontend | 결제 화면 | 실패 후 재결제 버튼이 보임 | FR3.3 |
| frontend | 완료 화면 | `CONFIRMED` 이벤트를 받으면 예약 완료 표시 | FR4.2 |

## 커버리지 목표

`poc` 범위는 새로 정한 커버리지 하한이 없습니다. 기존에 정한 목표를 낮추거나 끄지 않습니다. 요구사항마다 위 표의 테스트가 최소 1개 있는지를 기준으로 봅니다.

## 모킹·스텁 지침

- 서비스 간 호출(`payment-service`→`reservation-service`)은 단위 테스트에서 인터페이스를 가짜로 바꿉니다. API 테스트는 MockMvc로 서비스 하나만 띄웁니다.
- 시간(180초, 1초)은 `Clock`을 주입해 가짜 시계로 검증합니다. 실제로 기다리지 않습니다.
- 프런트의 SSE는 `EventSource` 가짜로 대체합니다.

## 테스트 데이터

- 샘플 주차권은 `recommendation-service`의 `sample-tickets.json` 한 파일에 있고, 테스트는 같은 파일 또는 테스트 전용 소규모 목록을 씁니다. 외부 서비스·네트워크는 쓰지 않습니다.

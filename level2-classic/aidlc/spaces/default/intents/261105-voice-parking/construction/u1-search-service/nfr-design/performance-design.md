# u1-search-service 성능 설계

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] nfr-requirements/performance-requirements.md(NFR3.1~NFR3.3), functional-spec.md(W1, W2), entities.md(목업 7건), tech-stack-decisions.md(Java 17, Spring Boot 3, 메모리 적재)

| 설계 | 방식 | 충족하는 요구사항 |
|---|---|---|
| 시작 시 메모리 적재 | 서비스가 기동할 때 목업 JSON을 읽어 주차장·주차권을 불변 목록과 장소별 색인(placeName → 주차장 목록)으로 만든다. 요청 중에는 파일·네트워크 입출력이 없다. | NFR3.3 |
| 요청당 계산 | 후보 최대 수십 건이므로 후보 수집 → 제외 → 점수 환산 → 합산 → 정렬을 한 번의 메모리 안 순회로 계산한다. 캐시는 두지 않는다(계산이 데이터 크기에 비해 충분히 빠르다). | NFR3.1 |
| 주차권 조회(C4) | passId로 해시맵 한 번 조회 후 계산한다. | NFR3.2 |
| 정수 계산 | 점수는 정수 비율로 계산해 부동소수점 오차 없이 반올림한다(0.5에서 올림). 요청마다 동일 결과를 낸다. | BR3.2, NFR3.1 |

## 성능 예산

| 구간 | 예산 |
|---|---|
| 입력 해석 | 20ms |
| 후보 수집·점수·정렬 | 30ms |
| 직렬화와 응답 | 50ms |
| 합계 목표 | 100ms 이하 (요구사항 2초의 5% 수준, 여유를 크게 둔다) |

## 비동기 처리, 풀링

비동기 처리와 연결 풀은 쓰지 않는다. 외부 호출과 DB 연결이 없다.

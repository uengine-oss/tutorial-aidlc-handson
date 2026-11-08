# u4-web-ui 논리 구성 요소

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] unit-of-work.md(U4), frontend-components.md, functional-spec.md, nfr-design의 성능·보안 설계, contract-summary.md

브라우저 안의 한 페이지와 그것을 서비스하는 개발 서버(프록시)로 이루어진다. 프로세스는 개발 서버 하나이고, 로직은 브라우저에서 돈다.

| 구성 요소 | 책임 | 적용되는 NFR 설계 |
|---|---|---|
| 화면 컴포넌트 (`App`, `QueryInput`, `ParkingCard`, `RecommendationList`, `ReservationStatus`, `ErrorNotice`, `EmptyNotice`, `ScenarioPanel`) | 상태를 보여 주기만 하고 판단하지 않는다. 텍스트로만 렌더링 | 텍스트 렌더링(보안), 즉시 반영(성능) |
| `useApp` | 두 영역의 화면 상태, 입력, 추천 결과, 미결 결제 시도와 `lastPaidPassId`, 경과 시간 틱(`now()` 주입) | 틱·복구 타이머 하나씩(성능), 멱등 키 보관(신뢰성) |
| `useReservationStream` | EventSource 구독 하나, 예약별 version 필터, 종료 시 닫기, 복구(1·2·5초, 60초) | 즉시 반영, 복구 한도 |
| `api` 모듈 | 상대 경로 fetch, `X-Session-Id` 헤더, 3초 제한 시간, 응답 형식 확인 | 세션 ID 처리·경계 검증(보안), 요청 제한 시간(성능) |
| `useSession` | 세션 ID 생성·보관(sessionStorage, 메모리 대체), 마지막 추천 목록 저장·복원 | 세션 ID 처리(보안) |
| 개발 서버 프록시 (`vite.proxy.js`) | `/api/search/**`→u1, `/api/reservations/**`→u2(SSE 포함), `POST /api/payments` 정확 일치→u3, 웹훅·`/internal` 미연결, 루프백 바인딩 검사 | 프록시 정확 일치(보안), 프록시는 통과만(성능) |
| 시험 지원 (가짜 `EventSource`·`fetch`, 가짜 시계, 시험용 SSE·u3 서버) | 시간 시험을 대기 없이 반복 가능하게 한다 | 시험 계층 L1·L2 |

## 실패 영역과 영향 범위

- 브라우저 탭이 사용자 단위 실패 영역이다. 탭의 오류는 서비스에 영향을 주지 않는다.
- 개발 서버가 죽으면 화면이 열리지 않는다. 서비스(u1~u3)는 영향이 없다. u5-local-run이 개발 서버 기동 실패를 알린다.
- 서비스가 죽으면 해당 요청이 실패 안내를 보이고 사용자가 다시 시도할 수 있다. 구독은 복구(최대 60초) 뒤 안내하고 멈춘다.
- 공유 자원은 없다. 상태는 탭 안의 메모리와 sessionStorage뿐이다.

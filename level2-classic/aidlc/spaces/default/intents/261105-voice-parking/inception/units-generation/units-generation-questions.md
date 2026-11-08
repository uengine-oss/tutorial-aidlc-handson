# Units Generation 질문

Domain Design에서 정한 컴포넌트 5개(QueryParser, Recommendation, Reservation, Payment, Notification)를 구현·배포 단위(Unit)로 묶는 질문입니다. 팀 규칙: Docker 없음, 별도 배포 환경 없음, 로컬 실행 스크립트로 기동, 마이크로서비스.

## Q1. [아키텍처] 백엔드 컴포넌트를 몇 개의 서비스로 묶을까요?
A. 3개 서비스 (제안)
   - search-service: QueryParser + Recommendation (주차권 조회·점수 계산)
   - reservation-service: Reservation + Notification (예약 상태·180초 취소·SSE 푸시)
   - payment-service: Payment (결제 요청·웹훅 처리)
   장점: Reservation과 Notification이 같은 프로세스라 상태 변화를 메모리에서 바로 푸시해 1초 이내 목표를 맞추기 쉽다. 단점: Notification을 따로 확장할 수 없다.
B. 5개 서비스 (컴포넌트마다 하나)
   장점: 경계가 가장 선명하다. 단점: 서비스 간 네트워크 호출이 늘고, 예약 상태 푸시가 한 번 더 네트워크를 건너 1초 목표에 불리하며 로컬 기동도 무거워진다.
X. Other (please specify)

[Answer]: A

## Q2. [아키텍처] 모의 PG는 어디에 둘까요?
요구사항에서 결제 승인은 비동기 웹훅으로 들어옵니다.

A. 별도의 작은 서비스(mock-pg-service)로 둔다 (제안)
   장점: 웹훅이 실제 HTTP 호출로 비동기로 들어와 실제 PG와 같은 경로를 시험할 수 있고, 승인·지연·실패 시나리오를 따로 조작할 수 있다. 단점: 기동할 프로세스가 하나 늘어난다.
B. payment-service 안에 내장
   장점: 프로세스가 적다. 단점: 웹훅이 내부 호출이 되어 실제 경로와 달라지고 실제 PG로 바꿀 때 구조가 바뀐다.
X. Other (please specify)

[Answer]: X. payment-service 안에 두되, 승인·실패 결과는 비동기로 자기 웹훅 엔드포인트를 HTTP로 호출하게 해서 실제 PG와 같은 경로로 시험할 수 있게 해 줘

## Q3. [아키텍처] 화면(Vue)은 백엔드를 어떻게 호출할까요?
A. 화면이 각 서비스를 직접 호출한다 (CORS 허용) (제안)
   장점: 구성요소가 늘지 않는다. 단점: 화면이 서비스 주소 3개를 알아야 한다.
B. 게이트웨이 서비스를 하나 더 둔다
   장점: 화면은 주소 하나만 안다. 단점: 서비스 하나가 늘고 SSE 중계를 거쳐야 해 1초 목표에 불리하다.
X. Other (please specify)

[Answer]: X. Vite 개발 서버 프록시로 각 서비스에 연결 (화면은 주소 하나만 알고 CORS 불필요)

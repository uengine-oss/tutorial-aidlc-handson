# Contract Design 질문

유닛 사이 연동(U4→U1, U4→U2, U4→U3, U2→U1, U3→U2, 모의 PG→U3 웹훅)은 모두 HTTP입니다. 서비스 포트는 U1 8081, U2 8082, U3 8083, Vite 5173으로 두고, Vite 프록시가 경로 접두사(`/api/search`, `/api/reservations`, `/api/payments`)로 나눠 보냅니다. 외부(시스템 밖) 소비자가 쓰는 API는 없습니다.

## Q1. [개발] 브라우저 세션(익명 사용자)은 서비스에 어떻게 전달할까요?
A. 화면이 처음 열 때 만든 임의 ID(UUID)를 브라우저에 저장하고 모든 요청에 `X-Session-Id` 헤더로 보낸다 (제안)
B. 서버가 쿠키로 세션을 발급한다
X. Other (please specify)

[Answer]: A

## Q2. [개발] 모의 PG가 보내는 웹훅의 위변조는 어떻게 막을까요?
A. 본문의 HMAC-SHA256 서명을 `X-Signature` 헤더로 보내고, 공유 비밀 값은 환경 변수로 주입한다. 이벤트 ID로 중복을 거른다 (제안)
B. 고정 토큰 하나를 헤더로 보내 비교한다
X. Other (please specify)

[Answer]: A

## Q3. [개발] 서비스 간 호출이 실패하거나 느릴 때 어떻게 할까요?
A. 호출 제한 시간 2초. 자동 재시도는 결과를 되돌릴 수 있는 호출(결제 결과 반영, 주차권 조회)만 최대 2회, 200ms 간격으로 한다. 예약 생성은 재시도하지 않고 결제 요청에 멱등 키를 둔다 (제안)
B. 자동 재시도 없이 실패하면 바로 오류를 돌려준다
X. Other (please specify)

[Answer]: A

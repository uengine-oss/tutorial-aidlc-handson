# User Stories 평가

## Decision

Execute

## Rationale

사용자(운전자)가 직접 쓰는 화면이 있고, 추천 점수 계산·비동기 결제 승인·시간 제한 자동 취소처럼 규칙이 있는 업무 흐름이 있어 스토리와 인수 조건으로 정리할 가치가 크다. 근거: requirements.md의 FR1~FR9, NFR1~NFR2.

## Factors considered

- 프로젝트 유형: 신규(greenfield) 프로토타입
- 사용자 대면 범위: 음성 텍스트 입력, 추천 결과, 결제, 예약 완료·취소 화면
- 복잡도 신호: 가중치 점수 계산, 웹훅 멱등 처리, 180초 경계 처리, 1초 이내 화면 전달

## Key areas where stories add the most value

- 추천 점수 계산: 숫자 예시를 인수 조건으로 고정
- 결제 승인·실패·지연의 세 가지 결과를 각각 사용자 관점으로 검증
- 승인 후 1초 이내 화면 전달과 180초 자동 취소

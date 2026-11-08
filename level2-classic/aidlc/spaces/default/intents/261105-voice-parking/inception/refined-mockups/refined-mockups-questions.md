# Refined Mockups 질문

이 워크플로(classic)는 러프 목업 단계가 없으므로 사용자 스토리와 요구사항에서 직접 화면을 설계합니다.

## Q1. [디자인] 음성 텍스트는 화면에서 어떻게 입력받나요?
요구사항 FR1.3에서 음성 인식(STT)은 범위 밖이고 이미 텍스트로 변환된 입력만 받기로 했습니다.

A. 텍스트 입력창 + 전송 버튼. 마이크 버튼은 만들지 않는다 (제안)
B. 위에 더해 브라우저 음성 인식 마이크 버튼도 둔다 (FR1.3 범위 확장)
X. Other (please specify)

[Answer]: A

## Q2. [개발] Vue 3 화면에 쓸 UI 컴포넌트 라이브러리가 있나요?
A. 없음. 직접 작성한 가벼운 CSS와 컴포넌트 (제안)
B. Vuetify
C. PrimeVue 또는 Element Plus
X. Other (please specify)

[Answer]: A

## Q3. [디자인] 접근성과 화면 크기는 어디까지 맞출까요?
A. WCAG 2.1 AA, 모바일(폭 360px)부터 데스크톱까지 반응형 (제안)
B. 데스크톱만, 키보드 조작과 색 대비 같은 기본 접근성만
X. Other (please specify)

[Answer]: B

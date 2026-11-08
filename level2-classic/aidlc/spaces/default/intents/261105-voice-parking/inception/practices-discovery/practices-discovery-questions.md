# Practices Discovery 질문

프로토타입(VoiceParking)에 맞는 팀 작업 방식을 정합니다. 각 질문의 제안 답은 프레임워크 기본값이며, 확정된 사실이 아닙니다.

## Q1. [개발] 얇은 end-to-end 조각을 먼저 만들까요?
워킹 스켈레톤이란 전체 흐름(음성 텍스트 → 추천 → 결제 → 예약 완료)을 끝까지 관통하는 최소 버전을 먼저 만들어, 조각들이 서로 연결되는지 증명한 뒤 실제 기능을 채우는 방식입니다.

A. 예, 먼저 만든다 (제안)
B. 아니오, 서비스별로 완성해 나간다
X. Other (please specify)

[Answer]: B

## Q2. [품질] 테스트는 어떤 방식으로 작성할까요?
기본값은 "구현 후 테스트 작성(test-after)"이며 classic 범위는 라인 커버리지 80%와 병합 전 CI 실행을 더합니다.

A. 구현 후 테스트 작성, 커버리지 80% 유지 (제안)
B. TDD (테스트 먼저)
C. BDD (Given/When/Then 시나리오 먼저, 단위 테스트는 구현 후)
D. 프로토타입이므로 핵심 흐름만 테스트 (커버리지 하한 없음)
X. Other (please specify)

[Answer]: B

## Q3. [운영] 브랜치와 배포는 어떻게 할까요?
기본값은 main 하나만 쓰는 trunk 방식이고, 병합 시 스테이징에 배포하며 운영 배포는 수동 승인입니다.

A. trunk(main) + 로컬 docker-compose 실행만, 별도 배포 없음 (프로토타입 제안)
B. trunk(main) + 병합 시 스테이징 자동 배포, 운영은 수동 승인 (프레임워크 기본값)
X. Other (please specify)

[Answer]: X. trunk(main), 로컬에서 실행 스크립트로만 실행하고 docker나 별도 배포 환경은 쓰지 않음

## Q4. [개발] 코드 스타일과 린터는 무엇을 쓸까요?
Vue 쪽과 Java 쪽 각각 포매터/린터가 필요합니다.

A. Vue: ESLint + Prettier, Java: Spotless(google-java-format) + Checkstyle (제안)
B. Vue: ESLint + Prettier, Java: 포매터 없이 IDE 기본
X. Other (please specify)

[Answer]: B

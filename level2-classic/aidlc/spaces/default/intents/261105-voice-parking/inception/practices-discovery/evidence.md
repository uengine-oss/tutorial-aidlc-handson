## 확인한 항목

- `aidlc/spaces/default/memory/org.md`: Way of Working, Walking Skeleton, Testing Posture, Deployment, Code Style의 프레임워크 기본값. 팀이 확정한 사실이 아니라 제안값이었고, 인터뷰에서 아래와 같이 확정 또는 대체되었다.
- 저장소 상태: 그린필드. 커밋이 없고 애플리케이션 코드, 빌드 설정, CI 설정, 린터 설정이 없다. 따라서 코드에서 발견한 관행은 없다.
- 프로젝트 설명(브리프): Vue 3 프런트엔드, Java Spring Boot 백엔드(모듈이 여럿이면 마이크로서비스), 음성 텍스트 기반 주차권 추천, 결제, 예약 완료 표시. 범위 `classic`, 깊이 `minimal`.

## 인터뷰 결정

- Q1 Walking skeleton: 답변 B. 얇은 end-to-end 조각을 먼저 만들지 않고 서비스별로 완성해 나간다.
- Q2 Testing: 답변 B. TDD(테스트 먼저). `classic` 범위의 80% 라인 커버리지와 병합 전 CI 실행 하한은 유지한다.
- Q3 Branching/deploy: 답변 X. trunk(main)만 사용하고 로컬에서 실행 스크립트로만 실행한다. Docker와 별도 배포 환경은 쓰지 않는다. 사람이 명시한 강제/금지 제약이므로 `discovered-rules.md`에 ALWAYS/NEVER 규칙으로 기록했다.
- Q4 Code style: 답변 B. Vue는 ESLint + Prettier, Java는 포매터/린터 없이 IDE 기본.

## 미해결 불확실성

- 마이크로서비스 분리 기준과 저장소 구성(모노레포 여부)은 이후 단계(설계)에서 정한다.
- CI 플랫폼은 미정이다. 병합 전 CI 실행 하한을 어떤 방식으로 충족할지는 Construction에서 결정한다.
- Construction 검증 명령(Verification Command)은 미정이며 해당 단계에서 사람이 승인한다.

# Team-Level Rules

> This team's affirmed practices and corrections. Loaded after `org.md` as
> strict-additive guidance; contradictions with broader policy are rejected.
> Populated by the practices-discovery affirmation gate. Edit at the gate,
> not directly.

## Way of Working

- 트렁크 기반 개발: 기본 브랜치 `main` 하나만 사용한다. Bolt 브랜치는 `main`으로 squash 병합하며 커밋 메시지는 Bolt slug로 한다.
- 장기 릴리스 브랜치를 두지 않는다. 별도 배포 환경이 없으므로 환경별 브랜치나 배포 설정도 두지 않는다.
- 요청된 커밋은 현재 브랜치에 만들고, 브랜치는 요청이 있을 때만 만든다.
- 구조: 프런트엔드 Vue 3, 백엔드 Java Spring Boot. 모듈이 여럿이면 마이크로서비스로 분리한다.

## Walking Skeleton

- 적용하지 않는다(Q1 답변 B). 얇은 end-to-end 조각을 먼저 만들지 않고 서비스별로 완성해 나간다.
- skeleton 체크포인트 의식(ceremony)은 건너뛴다.

## Testing Posture

- **Methodology**: tdd
- **Ordering**: 각 계층마다 실패하는 테스트를 먼저 작성한 뒤 그 테스트를 통과시키는 구현을 작성한다.
- `classic` 범위의 하한을 유지한다: 80% 라인 커버리지.
- `classic` 범위의 하한을 유지한다: 병합 전 CI 실행. 하한은 약화할 수 없다.

## Guard Policy

<!-- Affirmed by the team. Mode: strict, relaxed, or off. Strict here holds for every intent, whatever one person asks in chat; relaxed or off here applies to every intent whose policy came from its scope, unless project.md or the person sets another; changing this line changes it. A section under the retired Change Control heading, written by an earlier release, is still read. -->

## Deployment

- 별도 배포 환경(스테이징/프로덕션)을 두지 않는다. Docker도 쓰지 않는다.
- 로컬에서 실행 스크립트(run scripts)로만 실행한다.
- 따라서 병합 시 자동 배포와 프로덕션 수동 승인 게이트는 적용하지 않는다.

## Code Style

- Vue(프런트엔드): ESLint + Prettier를 사용한다.
- Java(백엔드): IDE 기본 설정 외에 별도 포매터/린터를 쓰지 않는다.
- 네이밍은 언어 관용을 따른다(JS/TS camelCase, Java 표준 관례).
## Forbidden

<!-- Team-specific forbidden patterns -->

## Mandated

<!-- Team-specific mandates -->

## Corrections

<!-- Self-learning loop appends here. -->

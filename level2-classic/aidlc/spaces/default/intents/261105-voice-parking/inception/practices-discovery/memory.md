<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-11-05T08:56:51Z — org.md 기본값을 모두 "초안, 인터뷰 대기"로 표시했다. 그린필드라 코드에서 발견한 관행이 없기 때문이다.

- 2026-11-05T08:59:58Z — 인터뷰 답변(Q1 B, Q2 B, Q3 X, Q4 B)을 반영해 초안 표시를 제거하고 team-practices.md를 확정했다. Docker/별도 배포 환경 금지와 로컬 실행 스크립트 필수는 사람이 명시한 제약이라 discovered-rules.md에 기록했다.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

- 2026-11-05T08:56:51Z — Testing Posture는 org.md 기본값인 test-after로 임시 기재했다. TDD/BDD 선호는 인터뷰에서 바뀔 수 있다.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-11-05T08:56:51Z — walking skeleton 적용 여부, 배포 환경 구성, Java/Vue 린터 도구, 마이크로서비스 분리 기준은 인터뷰에서 확인해야 한다.

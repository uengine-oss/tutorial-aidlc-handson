<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
- 2026-11-07T17:50:00Z — u5 NFR 요구 2차 검토(READY) 남은 항목, NFR Design/코드 생성 계획으로 닫는다: (Major) xtrace 커버리지 측정이 BASH_XTRACEFD(bash 4.1+)에 의존하는데 시험은 /bin/bash 3.2 → bash -x와 export한 PS4로 stderr를 파일에 받는 방식으로 정하고 서브셸·자식 bash·trap까지 수집하는 방법과 분모 규칙을 정한다, (Major) u4 준비 판정 'no 502'가 느슨함 → 5xx 전체 거부와 허용 상태 코드 목록(200·400·404·405 중 u1 공개 계약에서 고름), 연결 거부 시 500 가능성은 구현 때 확인, (Minor) set -m 세부(</dev/null, kill 실패 허용, trap 한 번만, 두 번째 Ctrl-C 시험), 비밀 값 최소 길이 검증(생성·사용자 값 모두 64자 미만 거부 또는 경고), 스모크의 웹훅 미도달 판정(상태 코드와 u3 로그 수신 기록 없음).
- 2026-11-07T16:10:00Z — u4 NFR 요구 검토(READY) 남은 항목, NFR Design에서 닫는다: (Major) 구간별 p99/최댓값 3배 통계가 NFR1의 "최대 1초" 판정을 대신하지 못함 → 최종 판정은 Build and Test 합산 측정, 구간 시험은 단위 가드라고 명시, 프록시 몫에도 최댓값 상한, (Major) 실제 브라우저 EventSource·렌더링을 재는 수단 없음(jsdom 한계) → 헤드리스 점검 또는 수동 확인 절차와 책임 소유자를 정한다, (Minor) NFR1.2 표본·클라이언트 명시와 웹훅 우회 합격 기준을 '시험용 u3가 받은 요청 수 0'으로, npm run verify 정의(커버리지+린트)와 coverage.include, 금지 API 목록 확대·allowedHosts·audit 판단 기준, 3초 제한 시간 근거, 틱 5ms 규칙.
- 2026-11-07T13:45:00Z — u3 NFR 요구 검토(READY, 조언) 남은 Major 2건, NFR Design에서 닫는다: (1) u2 예약 생성 호출 제한 1.5초는 계약 표(2초)와 다른 새 결정이며 호출하는 쪽이 더 짧은 제한을 쓰는 것으로 명시(출처 정정) (2) 웹훅 경로가 Vite 프록시 접두사 규칙으로 브라우저에 열릴 수 있어 서명 검증이 유일한 방어선 — u4·u5에 정확 일치 프록시 규칙을 넘기는 의무로 기록. Minor: Payment 단위 상태 갱신 원자성, 본문 8KB 한정 읽기, NFR5.1 문구, 모의 PG 자기 호출 제한 시간·스레드.
- 2026-11-07T12:30:00Z — u2 NFR 검토(READY, 조언) 남은 Major 2건: (1) u1 호출 재시도 총 시간(최대 약 6.4초)이 u3의 2초 제한보다 길어 고아 예약 가능, 총 마감 시간 요구 필요 (2) /internal 경로 무인증 — 루프백 바인딩 또는 공유 토큰 선택 필요. NFR Design에서 처리한다.
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

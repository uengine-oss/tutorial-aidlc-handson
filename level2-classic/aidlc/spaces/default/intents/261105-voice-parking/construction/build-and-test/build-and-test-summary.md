# 빌드와 시험 요약

## Sources

- [scope] Workflow-selected scope: classic, 시험 전략 minimal
- [memory:M1] u1~u5 code-generation-plan.md, unit-test-instructions.md, code-summary.md, 각 유닛 nfr-requirements/performance-requirements.md, team.md(80% 하한 약화 금지, Docker·별도 배포 환경 금지)
- 실행 증거: 이번 단계에서 직접 실행한 `mvn -q verify`, `npm --prefix services/u4-web-ui run verify`, `/bin/bash scripts/test/run-all.sh`, 실제 u1~u3 기동 후 직접 호출

## 준비 상태

- 빌드 안내는 `build-instructions.md`에 있다. `scripts/run-local.sh --prepare`가 통과했다.
- 실제 서비스 확인: u1~u3를 기동해 세 서비스 모두 `/actuator/health`가 `UP`이었다.
- 화면(u4, 포트 5173) 확인: 다른 프로젝트의 개발 서버가 `[::1]:5173`만 점유해 `run-local.sh`는 기동을 거부했다. u4는 127.0.0.1에 묶이므로 u1~u3와 u4를 각각 띄워 `smoke-local.sh`를 통과시켰다(5/5). 그 서버는 끄지 않았다. 실제 브라우저 확인은 사람이 직접 한다.

## 시험 결과 한눈에

| 대상 | 명령 | 결과 |
|---|---|---|
| u1~u3 | `mvn -q verify` | 실패: u3 `PerformanceTest` 20ms 두 항목(마지막 실행 기준, 부하 평균 약 54: 중복 웹훅 최댓값 32~53ms, 중앙값 0.4~0.7ms. 서명 거부는 직전 실행에서 실패, 다른 실행에서 최댓값 12.9ms). 첫 실행(부하 약 190)에서는 중복 웹훅 66.6ms. 나머지 통과 |
| u4 | `npm --prefix services/u4-web-ui run verify` | 통과. 시험 351 + 타이밍 4 + 프록시 19, 라인 98.29% |
| u5 | `/bin/bash scripts/test/run-all.sh` | 통과. 79 사례, 439 단언, 줄 커버리지 100.0% |
| u1~u3 실제 연동 | 직접 호출 | 추천 200, 빈 본문 400, 결제 202, 약 0.1초 만에 `CONFIRMED` |

## 커버리지 하한(80%)

| 유닛 | 라인 커버리지 | 하한 |
|---|---|---|
| u1 | 95.8% | 충족 |
| u2 | 96.6% | 충족 |
| u3 | 94.3% | 충족 |
| u4 | 98.29% | 충족 |
| u5 | 100.0% (run-local.sh) | 충족 |

하한은 낮추지 않았다.

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| u1 NFR3.1 | u1 performance-requirements | 추천 응답 100회 최댓값 ≤ 2초 | 시험 통과 | `mvn -q verify` u1 통과 | u1 code-generation | Met |
| u1 NFR3.2 | 같은 문서 | 주차권 조회 100회 최댓값 ≤ 500ms | 시험 통과 | 같은 실행 | u1 code-generation | Met |
| u2 NFR1.1~1.4 | u2 performance-requirements | 반영 150ms, 무구독 100ms, 첫 이벤트 200ms, 예약 생성 500ms | 시험 통과 | 같은 실행 | u2 code-generation | Met |
| u2 NFR2.1 (180초 ±2초) | 같은 문서 | 취소 178~182초 | 주입 시계 경계 시험 통과. 실제 180초 시험(`RealDeadlineSlowTest`, `-Pslow`) 통과: 예약 생성 11:17:19.196, 취소 푸시 11:20:19.089(약 179.9초) | 같은 실행, `-Pslow` 단독 실행 | u2 code-generation | Met |
| u2 NFR2.2 | 같은 문서 | 취소 스냅샷 150ms | 시험 통과 | 같은 실행 | u2 code-generation | Met |
| u3 NFR1.1 | u3 performance-requirements | 웹훅 처리 150ms | 시험 통과 | 같은 실행 | u3 code-generation | Met |
| u3 NFR1.2 | 같은 문서 | 중복 웹훅 100회 최댓값 ≤ 20ms | 66.6ms(부하 평균 약 190) | 같은 실행 실패 | u3 code-generation | Not Met |
| u3 NFR1.3 | 같은 문서 | 서명 불일치 거부 ≤ 20ms | 첫 실행 통과(최댓값 12.9ms), 11:01 재실행에서는 실패 | 같은 실행, 재실행 | u3 code-generation | Not Met |
| u3 NFR1.4~1.6 | 같은 문서 | 결제 요청 500ms, 502 1.7초, 모의 PG 시작 100ms | 시험 통과 | 같은 실행 | u3 code-generation | Met |
| u4 NFR1.1, NFR3.1 | u4 performance-requirements | 반영 p99 ≤ 100ms(jsdom) | 시험 통과 | `npm run verify` | u4 code-generation | Met |
| u4 NFR1.2 | 같은 문서 | 프록시 경유 지연 ≤ 100ms, 버퍼링 없음 | 프록시 시험 19건 통과 | `npm run verify` | u4 code-generation | Met |
| u4 NFR1.3 | 같은 문서 | 틱 p99 ≤ 5ms | 이번 실행 통과(부하에 민감) | `npm run verify` | u4 code-generation | Met |
| u4 NFR3.2, NFR1.4 | 같은 문서 | 3초 안내, 복구 1·2·5초 | 시험 통과 | `npm run verify` | u4 code-generation | Met |
| u5 스크립트 기동·종료·시크릿 처리 | u5 code-generation-plan | 80% 하한, 사례 통과 | 79 사례 통과, 100.0% | `run-all.sh` | u5 code-generation | Met |
| u5 실제 서비스 한 번에 기동(`run-local.sh`) | u5 계획 | u1→u2→u3→u4 준비 확인과 정리 | 다른 프로젝트가 IPv6(`[::1]:5173`)만 점유해도 포트 검사에서 거부함. 한 번에 기동·정리는 실제로 못 해 봄 | 오류 안내 확인 | u5 code-generation | Unverified |
| 화면 경유 스모크(`smoke-local.sh`, 127.0.0.1:5173) | u5 AC5.1.2 | 추천→결제→`CONFIRMED`, 웹훅·내부 경로 404/405 | 5/5 통과 | `.local-run/smoke.txt` | u5 code-generation | Met |
| 프로젝트 NFR1: 결제 승인 후 화면 완료 표시 ≤ 1초 | requirements.md NFR1 | 1초 이내 | 사람이 실제 브라우저에서 클릭부터 완료 표시까지 355ms 측정(서버 쪽 약 0.1초) | 브라우저 확인, 직접 호출 | project | Met |
| 화면 브라우저 동작(SSE 실시간 갱신, 지연 경과 표시, 180초 취소 표시, 실패 안내) | u4 계획 | 실제 브라우저에서 확인 | 사람이 직접 확인: 새로 고침 없이 갱신, 180초에 취소 문구 표시, 실패 표시 정상 | 브라우저 확인 | u4 code-generation | Met |
| 화면 브라우저 연결 6개 제한 | u4 계획 | 실제 브라우저에서 확인 | 확인하지 않음 | 없음 | u4 code-generation | Unverified |
| 추천 이유 문구의 조사 처리 | u1 AC2.2.5 | 자연스러운 문장 | "거리이(가) 가장 큰 강점입니다"처럼 조사가 그대로 나옴 | 브라우저 확인 | u1 code-generation | Not Met |
| u4 준비 확인 400 점검 | u5 설계 | u1에 빈 본문 요청 시 정확히 400 | 실제 u1이 400 `INVALID_TEXT` | 직접 호출 | u5 code-generation | Met |
| 웹훅·내부 경로가 화면 프록시에 닿지 않음 | u4 보안 요구 | 404/405 | 프록시 시험 19건으로 확인. 실제 5173 경유 확인은 못함 | `npm run verify` | u4 code-generation | Met |

## 판정 설명

- **Not Met**(u3 중복 웹훅 20ms, 서명 거부 20ms): 목표는 낮추지 않고 그대로 둔 열린 항목이다. 이 머신은 평소 부하가 40~60이라 낮은 부하를 기다리지 않기로 했으며(대기 작업 중단), 부하 약 39~54에서 마지막으로 돌린 결과 중앙값은 0.2~0.7ms인데 최댓값이 간헐적으로 13~53ms로 한도 20ms를 넘었다. 부하 때문인지 코드 때문인지는 가려지지 않았으므로 조용한 머신이나 CI에서 다시 확인한다.
- **Not Met**(추천 이유 조사 문구): 사람 결정으로 이번 프로토타입에서는 고치지 않는 Minor 미해결 항목이다.
- **Unverified**(`run-local.sh` 한 번에 기동·정리, 브라우저 연결 6개 제한): 다른 프로젝트가 IPv6로 5173을 점유해 `run-local.sh`가 거부했고, 연결 제한은 확인하지 않았다. 실패로 다루지 않으며 승인 때 그대로 알린다.
- u2 실제 180초 시험(`RealDeadlineSlowTest`, `-Pslow`)은 따로 돌려 통과했다(취소 약 179.9초, 허용 178~182초).

## 남은 확인

1. 조용한 머신이나 CI에서 u3 `PerformanceTest`의 중복 웹훅·서명 거부 20ms 두 항목을 다시 확인한다(목표 유지, 현재 Not Met).
2. 5173이 완전히 비면 `scripts/run-local.sh`로 한 번에 기동·정리를 확인한다(현재 Unverified).
3. 제안: Construction Verification Command를 `scripts/verify-all.sh`로 바꾸는 일은 사람의 승인이 필요하다.

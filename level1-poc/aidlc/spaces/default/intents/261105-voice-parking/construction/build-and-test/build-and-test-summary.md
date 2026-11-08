# 빌드·테스트 요약: VoiceParking 프로토타입

근거 문서: `code-generation-plan.md`(계획과 Testing Contract), `unit-test-instructions.md`(테스트 안내), `code-summary.md`(코드 요약). 상세는 `build-instructions.md`, `test-results.md`, `cross-unit-traceability.md`.

## 전체 상태

- 빌드: 성공 (백엔드 jar 3개, 프런트 번들)
- 테스트: 72개 중 72개 통과, 실패 0 (추천 18, 결제 19, 예약 19, 프런트 16)
- 요구사항 추적: 13개 모두 충족
- 서비스 3개를 실제로 띄운 확인: 추천 호출 정상, 승인 웹훅 → 확정 이벤트 도착 10회 모두 1초 이내(5~114ms), 결제 오류가 402 + `{code, message}`로 응답

## 테스트 종류

| 종류 | 만든 것 |
|---|---|
| 단위·API 테스트 | 서비스별 JUnit 5 + MockMvc, 프런트 vitest(jsdom) |
| 통합·성능·보안 테스트 안내 | 만들지 않음. Test Strategy가 Minimal이라 추가 안내 파일이 없고, 서비스 기동 확인은 `test-results.md`에 직접 기록 |

## 커버리지 기대

`poc` 범위에 커버리지 하한이 없어 수치는 정하지 않았고 측정하지도 않았습니다. 기준은 "요구사항마다 테스트가 있고 기존 테스트가 모두 통과"입니다.

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| NFR1 | requirements.md > NFR1 | 승인 웹훅을 서버가 받은 시점부터 사용자 화면에 예약 완료가 보일 때까지 1초 이내 | 서버가 웹훅을 받은 시점부터 확정 이벤트가 클라이언트에 도착하기까지 5~114ms, 10회 모두 1초 이내 (서비스 간 HTTP 구간 포함). 브라우저 화면 반영은 측정하지 않음 | `test-results.md` 서비스 기동 확인, `ReservationApiTest` | build-and-test | Met (서버→클라이언트 구간). 화면 표시는 아래 참고 |
| NFR2 | requirements.md > NFR2, FR3.4 | 승인 대기 180초, 넘으면 예약 자동 취소(정확히 180초는 취소) | 179초는 대기, 180초는 취소, 취소 뒤 승인은 무시됨 | `ReservationServiceTest`, `PaymentServiceTest` (가짜 시계) | build-and-test | Met |
| TC-STRATEGY | code-generation-plan.md > Testing Contract (Minimal) | 요구사항당 검증 1개 이상, 컴포넌트당 정상 경로 1개 이상 | 13개 요구사항 모두 테스트에 연결, 서비스·화면 컴포넌트마다 정상 경로 테스트 있음 | `cross-unit-traceability.md`, `test-results.md` | build-and-test | Met |
| TC-SUITE | code-generation-plan.md > Testing Contract (poc 범위) | 기존 테스트 모두 통과 | 72개 통과, 실패 0 | `test-results.md` | build-and-test | Met |
| TC-CI | code-generation-plan.md > Testing Contract | 계획이 CI Pipeline을 돌릴 때만 해당 | 이 계획에는 CI Pipeline이 없음 | 계획의 단계 목록 | - | N/A (계획에 CI Pipeline이 없음) |

## 준비 상태

- 빌드 준비: 됨
- 테스트 준비: 됨
- 배포 준비: 해당 없음. 이 작업은 로컬 프로토타입이고 배포 파일을 범위 밖으로 두었습니다.

## 알려진 한계와 남은 일

- **브라우저 확인은 사용자에게 남아 있습니다.** 화면의 SSE 구독, 결제 폴링, 카운트다운, 예약 완료 표시는 jsdom 테스트와 빌드까지만 했고 실제 브라우저에서는 열어 보지 않았습니다. NFR1의 "화면에 보인다" 부분과 FR4.2는 이 확인이 끝나야 완전히 닫힙니다.
- 결제 실패 통지(`result=FAILED` 웹훅)는 200으로 응답하고 실패 사유는 결제 상태로 조회합니다. 모든 결제 오류를 402로 통일하는 규칙에서 의도적으로 뺀 부분입니다.
- 내부 확정 API와 모의 PG 엔드포인트에는 인증이 없고, 웹훅 서명도 확인하지 않습니다. 시연용입니다.
- 코드 생성 단계 검토에서 남은 지적(예: 예약 서비스 장애 시 PG 재시도 없음, `App.vue`의 폴링 오류 처리)은 그대로 열려 있습니다.

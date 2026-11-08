# 유닛 간 추적표

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] 각 유닛 code-generation/traceability.json, requirements.md(FR1~FR9, NFR1~NFR9), `aidlc engine sensor-traceability --stage build-and-test` 실행 결과

## 결과

요구사항 FR 전부와 NFR1~NFR6, NFR8이 유닛 구현으로 이어진다. NFR7과 NFR9는 어느 유닛의 추적표에도 올라 있지 않아 도구 결과로는 미충족이다. 두 항목은 개별 코드가 아니라 개발 방식·스타일 규칙이라 이 단계의 실행 증거로 대신 확인했다.

## 요구사항 → 유닛

| 요구사항 | 유닛 | 상태 |
|---|---|---|
| FR1.x (음성·텍스트 입력 해석) | u1, u4 | 충족 |
| FR2.x, FR3.x (추천, 점수·이유) | u1, u4 | 충족 |
| FR4.x (결제 요청·예약 생성) | u2, u3, u4 | 충족 |
| FR5.x, FR6.x (승인·실패·웹훅·실시간 반영) | u2, u3, u4 | 충족 |
| FR7.x (180초 자동 취소) | u2, u4 | 충족 |
| FR8.1 | u1, u4 | 충족 |
| FR9.1 (로컬 실행) | u5 | 충족 |
| NFR1 (1초 안에 완료 표시) | u2, u3, u4 | 충족(화면 구간 실측은 Unverified) |
| NFR2 (180초 자동 취소 정확도·오류 처리) | u1, u2 | 충족(실제 180초 시험 통과, 취소 약 179.9초) |
| NFR3 (추천 2초) | u1, u4 | 충족 |
| NFR4, NFR5 | u1, u2, u3 | 충족 |
| NFR6 (보안) | u1~u5 | 충족 |
| NFR8 (로컬 실행·포트·Docker 금지) | u1~u5 | 충족 |
| NFR7 (TDD, 라인 커버리지 80%, 병합 전 CI) | 모든 유닛 | 추적표 없음. 커버리지는 u1 95.8%, u2 96.6%, u3 94.3%, u4 98.29%, u5 100%로 하한 충족. 병합 전 CI는 별도 배포 환경·CI 서버가 없어 `scripts/verify-all.sh`를 로컬 검증으로 대신함 |
| NFR9 (Java IDE 기본, Vue ESLint+Prettier) | u4 (Vue) | 추적표 없음. u4의 `npm run verify`에 lint가 들어 있고 통과함 |

## 이 단계에서 고친 것

u1의 `code-generation/traceability.json`이 항목마다 파일 두 개(소스, 시험)를 쉼표로 적고 있어 도구가 "대상 파일 없음"으로 읽었다. 각 항목을 주 소스 파일 하나로 고쳤다(다른 유닛과 같은 형식). 내용상 연결은 바뀌지 않았고 시험 파일 이름은 `unit-test-instructions.md`에 남아 있다.

## 남은 틈

- NFR7, NFR9를 유닛 추적표에 올리는 일은 후속 변경으로 둔다.
- u5 코드 검토에서 이미 기록된 누락(NFR7.x, 8.2, 8.4, 8.8, 8.9, 9.1, 4.1)과 같은 종류다.

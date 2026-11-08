# u1-search-service 보안 설계

## Sources

- [scope] Workflow-selected scope: classic
- [Q4] 로그인 없음, 익명 세션 (requirements-analysis Q4)
- [memory:M1] nfr-requirements/security-requirements.md(NFR6.1~NFR6.4, NFR8.1), rules.md(BR1.5, BR1.6, BR4.2), contract-summary.md

| 설계 | 방식 | 충족하는 요구사항 |
|---|---|---|
| 비밀 값 없음 | 이 서비스는 외부 서비스·DB를 쓰지 않아 비밀 값이 없다. 소스·설정에 키나 비밀번호 문자열을 두지 않는다. 빌드 단계에서 흔한 비밀 값 패턴 검색을 시험으로 넣는다. | NFR6.1 |
| 경계 입력 검증 | 컨트롤러에서 본문 `text`(비어 있지 않음, 200자 이하), `X-Session-Id`(UUID 형식), C4의 `durationHours`(정수, 1 이상, 상한은 아래 가정)를 검증하고 어긋나면 400을 돌려준다. 검증은 서비스 로직보다 앞에서 한다. | NFR6.2 |
| 입력 원문 비반사 | 입력 문장은 문자열 일치(등록된 장소 이름)와 숫자 패턴 추출에만 쓴다. 쿼리로 해석하거나 실행하지 않고, 오류 본문에는 입력 원문을 넣지 않는다(고정 문구와 오류 코드만). | NFR6.3 |
| 인증·인가 없음 | 인증이 없다. 세션 ID는 식별용이며 이 서비스는 세션별 데이터를 저장하지 않는다. 인증을 우회하는 코드를 만들지 않는다. | NFR6.4 |
| 내부 경로 분리 | C4 경로는 `/internal/` 접두사로 두고 화면의 Vite 프록시에 연결하지 않는다. u2-reservation-service만 직접 호출한다. | NFR6.4, 위협 고려 |
| 주소는 환경 변수 | 이 서비스는 다른 서비스를 호출하지 않는다. 포트(기본 8081)는 환경 변수 `SERVER_PORT`로 바꿀 수 있다. | NFR8.1 |

## 보안 헤더, 암호화

별도 보안 헤더와 암호화는 적용하지 않는다. 로컬 환경에서만 동작하고 민감 데이터가 없다.

## Assumptions & Open Questions

- [assumption] C4의 `durationHours` 상한은 24시간이다(BR1.2의 이용 시간 범위와 같다). 24를 넘으면 400 INVALID_DURATION을 돌려준다. 기능 설계 검토에서 이 상한이 정의되지 않았다는 지적이 있어 이 설계에서 정한다. 계약 C4에도 같은 상한을 반영해야 한다.

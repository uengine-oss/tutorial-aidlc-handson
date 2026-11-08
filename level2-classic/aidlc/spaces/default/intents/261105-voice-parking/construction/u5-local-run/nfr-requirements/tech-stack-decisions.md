# u5-local-run 기술 스택 결정

## Sources

- [scope] Workflow-selected scope: classic
- [memory:M1] team.md(Docker 금지, 별도 배포 환경 금지, 로컬 실행 스크립트 필수, TDD, 커버리지 80%와 병합 전 CI 실행, 하한은 약화할 수 없음), project.md(Mandated: 로컬 실행 스크립트, Forbidden: Docker·별도 배포 환경), requirements.md의 NFR4, NFR7, NFR8, NFR9, unit-of-work.md(U5), stories.md의 US5.1(AC5.1.1~AC5.1.3), u1~u4의 code-summary.md(실행 명령, 환경 변수, 기동 순서, 앞 유닛이 u5에 넘긴 사항), security-requirements.md

이 머신에서 확인한 도구: bash(`/bin/bash` 3.2와 zsh), Java 17, Maven 3.9.12, Node 25.2.1 / npm 11.6.2, `curl`, `lsof`, `openssl`(없으면 `od`로 대체). `shellcheck`와 `bats`는 없다.

| 영역 | 선택 | 이유 | 요구사항 |
|---|---|---|---|
| 스크립트 언어 | bash (macOS 기본 bash 3.2 호환, `#!/usr/bin/env bash`, `set -euo pipefail`). 연관 배열 같은 bash 4 기능은 쓰지 않는다 | 저장소 루트에서 추가 설치 없이 돈다 | NFR8, NFR9.1 |
| 구성 | `scripts/run-local.sh`(실행·`--prepare`·`--help`), `scripts/smoke-local.sh`(연동 스모크), `scripts/verify-all.sh`(전체 검증), 시험 `scripts/test/` | 설정 구역은 `run-local.sh` 맨 위 한곳 | NFR8.1, NFR8.6 |
| 서비스 기동 | u1·u2·u3: `mvn -q -pl services/<서비스> spring-boot:run`, u4: `npm --prefix services/u4-web-ui run dev`. 각각 `security-requirements.md`의 주입 변수 표대로 환경 변수를 넘긴다 | 각 유닛의 code-summary.md가 정한 실행 명령 | NFR8 |
| 기동 순서 | u1 → u2 → u3 → u4, 앞 서비스가 준비된 뒤 다음을 띄운다 | 의존 사슬(u2→u1, u3→u2, u4→u1~u3) | NFR4 |
| 프로세스 그룹 | 스크립트가 `set -m`(작업 제어)으로 서비스마다 자기 프로세스 그룹에서 시작한다. 종료는 그룹 전체에 신호를 보낸다(`kill -TERM -- -<그룹 ID>`) | macOS에 `setsid`가 없고 Maven·npm이 자식·손자를 만든다. 그룹에 보내야 JVM과 node까지 끝난다 | NFR8.4 |
| 준비 확인 | `curl --silent --max-time 2`로 1초 간격으로 폴링. u1~u3은 `/actuator/health` 본문에 `"status":"UP"`이 있으면 준비(`grep -q`), u4는 `/` 응답 코드 200과 프록시 점검으로 `/api/search/`가 연결 가능한 u1 주소로 전달됨(응답 코드가 502가 아님). 서비스당 제한 90초 | 각 서비스가 상태 확인 경로를 제공한다 | NFR8.9 |
| 포트 점검 | 기동 전 `lsof -nP -iTCP:<포트> -sTCP:LISTEN`으로 사용 중인지 확인 | 어느 포트가 사용 중인지 알리고 거부한다 | NFR8.3 |
| 난수 비밀 값 | `openssl rand -hex 32`, 없으면 `od -An -N32 -tx1 /dev/urandom \| tr -d ' \n'` | 추가 설치 불필요, 256비트 | NFR6.1 |
| 로그 | `.local-run/logs/<서비스>.log` (`umask 077`, 실행마다 덮어쓰기) | 서비스별 로그 | NFR6.5 |
| 시험 | bash 시험 스크립트 `scripts/test/run-local.test.sh`, 실제 서비스 대신 시험용 가짜 서비스(Node 서버를 셸 래퍼로 감싸 자식·손자 프로세스를 만드는 형태)를 환경 변수로 주입한다. 시험은 `/bin/bash`(3.2)로 실행한다 | `bats`가 없고 추가 설치를 요구하지 않는다. 손자 프로세스를 재현해 종료 정리를 시험한다 | NFR7 |
| 커버리지 측정 | 설치 없는 줄 단위 측정: 시험 실행 중 `BASH_XTRACEFD`와 `PS4='+${BASH_SOURCE}:${LINENO}:'`로 `run-local.sh`에서 실행된 줄을 모으고, 실행 가능한 줄(빈 줄·주석·`fi`/`done`/`esac`/`}`만 있는 줄 제외)의 수와 비교해 비율을 계산한다. 80% 미만이면 실패 | 팀의 80% 라인 하한을 약화하지 않는다. 근사 측정이며 한계(함수 안에서 실행되지 않은 분기 줄은 세지만, 한 줄 안의 조건 분기는 세지 못함)를 코드 요약에 적는다 | NFR7.2 |
| 정적 검사 | `bash -n`과 `/bin/bash` 3.2로 시험 실행. `shellcheck`가 있으면 함께 실행하고 없으면 건너뛴다고 알린다 | 셸에는 팀의 별도 포매터·린터 규칙이 없다 | NFR9.1 |

## 요구사항

| ID | 요구사항 | 측정 방법 | 출처 |
|---|---|---|---|
| NFR8.2 | Docker를 쓰지 않는다. 스크립트 한 번(`scripts/run-local.sh`)으로 u1~u4가 모두 기동되고 접속 주소를 화면에 알린다. 스크립트는 시작한 뒤 포그라운드에서 대기하며 Ctrl-C가 유일한 정지 수단이다. | 시험: 스크립트 실행 후 네 서비스(가짜)가 응답, Docker 명령 미사용, 포그라운드 유지 | NFR8, US5.1 AC5.1.1 |
| NFR8.3 | 기동하지 못한 서비스가 있으면 어느 서비스가 왜 못 떴는지(포트 사용 중, 준비 시간 초과, 프로세스 종료, 준비되지 않은 빌드)를 오류 메시지로 알리고 그 서비스 로그의 마지막 20줄을 보이며 이미 띄운 서비스를 모두 종료하고 0이 아닌 종료 코드로 끝난다. | 시나리오 시험: 포트 사용 중, 서비스가 즉시 종료, 준비 시간 초과, 준비되지 않은 빌드 | US5.1 AC5.1.3 |
| NFR8.4 | 사용자가 스크립트를 중단(Ctrl-C)하거나 종료하거나 기동에 실패하면 띄운 모든 프로세스(Maven이 만든 자식 JVM과 npm이 만든 node의 자식·손자 포함)를 종료한다. 순서: 각 그룹에 SIGTERM → 10초 유예 → 남아 있으면 SIGKILL → 네 포트가 닫혔는지 확인해 열려 있으면 경고. 종료 후 남는 프로세스가 없어야 한다. | 시험: 자식·손자 프로세스를 만드는 가짜 서비스로 중단 신호 후 프로세스·포트가 없음, SIGTERM을 무시하는 가짜는 SIGKILL로 끝남 | NFR8 |
| NFR8.5 | 서비스당 준비 확인 제한은 90초다(합계 제한이 아니다). 준비가 안 된 상태에서 첫 실행의 컴파일·의존성 내려받기 시간이 이 제한에 들지 않도록 `scripts/run-local.sh --prepare`를 별도 동작으로 둔다. 컴파일된 상태(`prepare` 뒤)에서 서비스당 준비까지 걸린 시간을 재서 화면에 알린다. 준비 목표: 조용한 머신에서 서비스당 60초 이하. | `--prepare`가 컴파일과 `npm install`을 실행하는 시험(가짜 도구 명령으로 호출 확인), 준비 시간 제한 초과 시험, 실제 서비스를 띄운 스모크에서 서비스별 준비 시간 기록 | NFR8, AC5.1.1 |
| NFR8.6 | 기동 순서·포트·환경 변수는 스크립트 맨 위 한 구역에 있고 `--help`가 환경 변수 목록과 기본값, 사용법(`--prepare`, `--help`, 포그라운드 대기와 Ctrl-C)을 보인다. | `--help` 출력 시험 | NFR8.1 |
| NFR8.7 | 연동 스모크 `scripts/smoke-local.sh`가 실행 중인 전체 서비스에서 AC5.1.2를 자동으로 확인한다: 화면 프록시(5173)를 통해 `POST /api/search/recommendations`로 "강남역 근처 2시간 주차권 찾아줘"를 보내 후보를 받고, 최적 후보로 `POST /api/payments`(승인 시나리오)를 요청한 뒤 `GET /api/reservations/current`가 `CONFIRMED`가 될 때까지 5초 안에 폴링한다. 같은 스모크가 프록시를 통한 `POST /api/payments/webhook`과 `/internal/reservations`가 서비스에 닿지 않음도 확인한다. 결과(단계별 성공·실패와 걸린 시간)를 `.local-run/smoke.txt`에 남긴다. 이 스모크는 Build and Test의 실제 서비스 실행에서 쓴다. | 가짜 서비스 응답으로 스모크 스크립트 자체를 시험하고, 실제 서비스에서의 실행은 Build and Test의 증거로 한다 | US5.1 AC5.1.2, NFR6.6 |
| NFR8.8 | 기동이 끝난 뒤 서비스 하나가 종료되면(준비 확인 통과 후) 어느 서비스가 종료되었는지 알리고 나머지를 모두 종료하며 0이 아닌 종료 코드로 끝난다. 감시 간격은 2초다. | 시나리오 시험: 기동 뒤 가짜 서비스를 종료시킴 | NFR8 |
| NFR8.9 | 준비 확인 규칙: 폴링 간격 1초, `curl` 시간 제한 2초, u1~u3은 `/actuator/health` 본문에 `"status":"UP"`이 있을 때만 준비로 본다. u4는 `/`가 200이고 u1 주소로 향하는 프록시 경로 `/api/search/`가 502가 아닌 응답(예: 405나 404)일 때 준비로 본다(프록시 대상이 닿는지 확인). | 시나리오 시험: 비정상 본문, 502, 시간 초과 | NFR8 |
| NFR7.1 | 각 동작마다 실패하는 시험을 먼저 쓴 뒤 구현한다(TDD). 요구사항마다 시나리오 시험 하나 이상: 기동 성공, 포트 사용 중, 서비스가 즉시 종료, 준비 시간 초과, 준비되지 않은 빌드, 중단 정리(자식·손자 포함), SIGKILL 필요, 기동 뒤 종료 감시, 잘못된 설정값, 같은 포트 둘, 비루프백·`UI_ALLOW_NON_LOOPBACK` 거부, 비밀 값 처리(길이·상속 차단·출력 없음), 주입 변수 도출(U3 포트 변경이 웹훅 주소에 반영), 로그 권한과 덮어쓰기, `--help`, `--prepare`. 시험은 `/bin/bash`(3.2)로 실행한다. | 시험 스크립트 통과 | NFR7, team.md Testing Posture |
| NFR7.2 | 셸 스크립트의 라인 커버리지는 xtrace 기반 측정(위 표)으로 80% 이상이어야 하며 미달이면 시험이 실패한다. 하한은 낮추지 않는다. 측정이 근사라는 한계를 코드 요약에 적는다. | `scripts/test/coverage.sh`가 계산한 비율 ≥ 80% | NFR7, team.md Testing Posture |
| NFR7.3 | 전체 검증 스크립트 `scripts/verify-all.sh [--slow]`가 병합 전 검증을 한 번에 실행한다: `mvn -q verify`(`--slow`이면 `-Pslow` 포함, u2의 실제 180초 취소 시험), `npm --prefix services/u4-web-ui run verify`, 이 유닛의 시험과 커버리지. 하나라도 실패하면 0이 아닌 종료 코드로 끝나며 모든 단계를 실행해 어느 단계가 실패했는지 요약한다. 부하 때문에 간헐 실패하는 시간 시험을 가리지 않는다: 시작 때 부하 평균을 출력하고 부하가 60 이상이면 "시간 시험이 흔들릴 수 있습니다"를 경고한다. 프로젝트의 Construction Verification Command를 이 스크립트로 바꾸는 것은 Unit 체크포인트에서 사람이 승인한다(지금 승인된 명령은 `mvn -q verify && npm --prefix services/u4-web-ui run verify`). | 시험: 가짜 명령으로 단계 성공·실패 조합, 부하 경고 | NFR7, team.md(병합 전 CI 실행), u2·u3·u4가 넘긴 사항 |
| NFR9.1 | 셸 스크립트는 `bash -n`을 통과하고 `/bin/bash`(3.2)에서 시험이 돈다. `set -euo pipefail`, 모든 변수 확장에 따옴표, 함수 단위 구성, 이름은 소문자 스네이크 케이스를 따른다. `shellcheck`가 있으면 오류 없이 통과한다. | `bash -n`, 3.2 시험 실행, 코드 검토 | NFR9 |
| NFR4.1 | 스크립트는 각 유닛의 공개된 실행 명령과 환경 변수만 쓰고 서비스 코드를 직접 건드리지 않는다. 유닛 하나를 바꿔도 스크립트는 환경 변수·명령이 그대로면 영향이 없다. | 설계 확인 | NFR4 |

## 대안

- docker compose: 팀 규칙과 NFR8이 금지한다.
- Makefile / 루트 package.json 스크립트: bash 하나로 충분하고 새 도구를 더한다. 선택하지 않았다.
- `bats`·`shunit2`: 설치가 필요해 선택하지 않았다.
- 서비스를 `java -jar`로 직접 띄우기: 먼저 `mvn package`를 요구하고 시간이 더 걸려, 각 유닛의 문서화된 `spring-boot:run`을 쓴다.
- 커버리지 면제(측정 불가 한계를 사람이 승인): xtrace로 설치 없이 측정할 수 있어 팀의 하한을 약화하지 않는 쪽을 택했다.

## Assumptions & Open Questions

- [assumption] xtrace 기반 줄 커버리지는 근사다. 한 줄에 여러 조건이 있는 줄은 한 번 지나가면 실행된 것으로 센다. 이 한계 안에서 80%를 지킨다.
- [assumption] 첫 실행의 컴파일·의존성 내려받기는 `--prepare`가 담당한다. `--prepare` 없이 실행하면 준비되지 않은 빌드(`target/classes` 없음 또는 `services/u4-web-ui/node_modules` 없음)를 감지해 `--prepare`를 안내하고 거부한다.
- [assumption] 서비스당 준비 시간 목표 60초와 제한 90초는 이 머신의 조용한 상태 기준 값이다. 부하가 높을 때는 제한을 넘을 수 있고 그때 실패 원인은 "준비 시간 초과"로 정확히 알린다.
- 열린 질문: 없음.

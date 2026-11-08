# Claude Code × AI-DLC 핸즈온 — 실습 산출물

uEngine AX School 튜토리얼 **[Claude Code × AI-DLC 핸즈온](https://www.uengine.org/docs/aidlc-handson/)** 을 실제로 실행해서 남은 파일을 그대로 모은 저장소입니다.
튜토리얼 각 장에서 가리키는 산출물(요구사항, 유저 스토리, 설계, 유닛 계획, 코드, 시험, 감사 기록)을 이곳에서 직접 열어 볼 수 있습니다.

- 만든 것: **VoiceParking** 프로토타입. "강남역 근처 2시간 주차권 찾아줘"라는 텍스트를 받아 주차권 세 개를 비교해 추천하고, 모의 결제가 승인되면 예약을 확정합니다.
- 기술: Vue 3 + Java 17 Spring Boot 마이크로서비스. Docker와 외부 API 키는 쓰지 않습니다.
- 실행 환경: AWS Labs [AI-DLC](https://github.com/awslabs/aidlc-workflows) 2.11.2, Claude Code 2.1.318, 모델 Claude Sonnet 5.5(medium). 2026년 11월 5~8일에 실행했습니다.

## 폴더

| 폴더 | 튜토리얼 | AI-DLC 스코프 | 상태 |
|---|---|---|---|
| [`level1-poc/`](level1-poc/) | 레벨 1 · 1~4장 | `poc` (가장 짧은 경로) | 완료. 서비스 3개 + 화면 |
| [`level2-classic/`](level2-classic/) | 레벨 2 · 5~14장 | `classic` (단계마다 문서와 승인) | 완료(COMPLETE). 유닛 5개 |

두 폴더 모두 같은 구조입니다.

```
aidlc/spaces/default/
├─ memory/                     팀·프로젝트 규칙 (org.md, team.md, project.md, phases/)
└─ intents/261105-voice-parking/
   ├─ aidlc-state.md           어느 단계까지 왔는지
   ├─ audit/                   감사 기록 (질문, 답, 승인, 검증 결과)
   ├─ ideation/  inception/    의도·요구사항·스토리·설계·유닛·볼트 계획
   └─ construction/            유닛별 기능 설계·NFR·코드 계획·결과, 빌드와 테스트
```

## 장별 산출물

### 레벨 1 · poc

| 장 | 산출물 |
|---|---|
| [1. 설치와 프로젝트 준비](https://www.uengine.org/docs/aidlc-handson/setup.html) | [`memory/`](level1-poc/aidlc/spaces/default/memory/) |
| [2. 의도 정리와 요구사항](https://www.uengine.org/docs/aidlc-handson/requirements.html) | [`ideation/intent-capture/`](level1-poc/aidlc/spaces/default/intents/261105-voice-parking/ideation/intent-capture/) · [`requirements.md`](level1-poc/aidlc/spaces/default/intents/261105-voice-parking/inception/requirements-analysis/requirements.md) |
| [3. 코드 생성과 학습](https://www.uengine.org/docs/aidlc-handson/construction.html) | [`construction/code-generation/`](level1-poc/aidlc/spaces/default/intents/261105-voice-parking/construction/code-generation/) · [`backend/`](level1-poc/backend/) · [`frontend/`](level1-poc/frontend/) · [`project.md`](level1-poc/aidlc/spaces/default/memory/project.md) |
| [4. 동작 확인과 감사 기록](https://www.uengine.org/docs/aidlc-handson/run.html) | [`RUN.md`](level1-poc/RUN.md) · [`audit/`](level1-poc/aidlc/spaces/default/intents/261105-voice-parking/audit/) |

### 레벨 2 · classic

산출물 경로 앞부분 `level2-classic/aidlc/spaces/default/intents/261105-voice-parking/` 은 생략했습니다.

| 장 | 산출물 |
|---|---|
| [5. 개요와 1단계 팀 규칙](https://www.uengine.org/docs/aidlc-handson/l2-overview.html) | [`inception/practices-discovery/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/inception/practices-discovery/) · [`team.md`](level2-classic/aidlc/spaces/default/memory/team.md) |
| [6. 2단계 요구사항](https://www.uengine.org/docs/aidlc-handson/l2-requirements.html) | [`inception/requirements-analysis/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/inception/requirements-analysis/) |
| [7. 3·4단계 유저 스토리와 화면](https://www.uengine.org/docs/aidlc-handson/l2-stories.html) | [`inception/user-stories/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/inception/user-stories/) · [`inception/refined-mockups/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/inception/refined-mockups/) |
| [8. 5단계 도메인 설계](https://www.uengine.org/docs/aidlc-handson/l2-domain.html) | [`inception/domain-design/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/inception/domain-design/) |
| [9. 6·7단계 유닛과 API 계약](https://www.uengine.org/docs/aidlc-handson/l2-units.html) | [`inception/units-generation/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/inception/units-generation/) · [`inception/contract-design/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/inception/contract-design/) |
| [10. 8단계 볼트 계획](https://www.uengine.org/docs/aidlc-handson/l2-bolts.html) | [`inception/delivery-planning/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/inception/delivery-planning/) |
| [11. 구현: 유닛 u1](https://www.uengine.org/docs/aidlc-handson/l2-build.html) | [`construction/u1-search-service/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/construction/u1-search-service/) · [`services/u1-search-service/`](level2-classic/services/u1-search-service/) |
| [12. 구현: 예약·결제 서비스(u2·u3)](https://www.uengine.org/docs/aidlc-handson/l2-build-services.html) | [`construction/u2-reservation-service/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/construction/u2-reservation-service/) · [`construction/u3-payment-service/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/construction/u3-payment-service/) · [`services/`](level2-classic/services/) |
| [13. 구현: 화면·실행 스크립트(u4·u5)](https://www.uengine.org/docs/aidlc-handson/l2-build-ui-run.html) | [`construction/u4-web-ui/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/construction/u4-web-ui/) · [`construction/u5-local-run/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/construction/u5-local-run/) · [`services/u4-web-ui/`](level2-classic/services/u4-web-ui/) · [`scripts/`](level2-classic/scripts/) |
| [14. 빌드·테스트와 앱 동작](https://www.uengine.org/docs/aidlc-handson/l2-test.html) | [`construction/build-and-test/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/construction/build-and-test/) |
| [산출물 찾아보기](https://www.uengine.org/docs/aidlc-handson/artifacts.html) | [`aidlc-state.md`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/aidlc-state.md) · [`audit/`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/audit/) |

레벨 2의 최종 판정은 [`build-and-test-summary.md`](level2-classic/aidlc/spaces/default/intents/261105-voice-parking/construction/build-and-test/build-and-test-summary.md)에 있습니다. 목표 22개 중 Met 17, Not Met 3, Unverified 2입니다. 맞추지 못한 목표도 낮추지 않고 그대로 기록했습니다.

## 직접 실행해 보기

준비물: Java 17, Maven 3.9, Node 20 이상.

```bash
# 레벨 1
cd level1-poc && bash run-all.sh      # 처음 실행할 때 npm install 을 함께 합니다. 자세한 내용은 RUN.md

# 레벨 2
cd level2-classic && bash scripts/run-local.sh --prepare   # 처음 한 번: 컴파일과 npm install
bash scripts/run-local.sh                                     # 서비스 4개 기동, 종료는 Ctrl+C
```

두 앱 모두 브라우저에서 http://localhost:5173 을 엽니다. 레벨 2 전체 검증 명령은 `mvn -q verify && npm --prefix services/u4-web-ui run verify` 입니다.

## 이 저장소에 없는 것

- `.claude/` — AI-DLC 프레임워크 사본(스킬, 훅, 에이전트)입니다. 직접 따라 하려면 [1장](https://www.uengine.org/docs/aidlc-handson/setup.html)처럼 `curl -fsSL https://github.com/awslabs/aidlc-workflows/releases/latest/download/install.sh | sh` 로 설치하고 `aidlc config --harness claude` 로 연결합니다.
- `node_modules/`, `target/`, `dist/`, 실행 로그 — 빌드하면 다시 생깁니다.
- AI-DLC가 내부용으로 무시하는 파일(`.aidlc-engine/`, 작업 커서 등) — 프로젝트의 `.gitignore` 를 따랐습니다.

## 알아 둘 점

- 모든 문서와 코드는 AI가 만들고 사람이 승인한 결과입니다. 오타나 어색한 문장도 고치지 않았습니다. 교육용 프로토타입이므로 운영 환경에 그대로 쓰지 마십시오.
- 같은 요청을 다시 실행해도 모델이 쓰는 문장, 질문 수, 파일 구성은 조금씩 달라집니다.
- 감사 기록에는 실행한 머신의 경로가 그대로 남아 있습니다. 기록을 손대지 않고 그대로 두었습니다.

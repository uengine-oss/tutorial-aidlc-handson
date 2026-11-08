<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
- 2026-11-08T01:30:00Z — (Minor, 미해결, 사람 결정으로 이번 프로토타입에서는 고치지 않음) u1 추천 이유 문구에 조사가 그대로 나온다("거리이(가) 가장 큰 강점입니다"). 브라우저 확인에서 발견. ReasonFormatter의 조사 처리가 후속 과제다.
- 2026-11-08T00:00:00Z — u5 코드 검토(READY, 조언) 남은 Minor 9건: 기동 전 lsof·curl·node·mvn·npm 존재 확인 없음(lsof 없으면 포트 점검 무력), 커버리지 100%는 줄 도달률이고 한 줄 분기 27곳·정리 뒤 열린 포트 경고(줄 388)는 시험 없음, 80% 하한이 run-local.sh에만 걸리고 smoke-local.sh·verify-all.sh는 제외(사람 승인 필요), smoke-local.sh의 SMOKE_CONFIRM_TIMEOUT 등 검증 없음(산술 확장), verify-all.sh의 VERIFY_U5_SCRIPT 환경 변수로 u5 단계 우회 가능, 환경 격리가 SPRING_APPLICATION_JSON·MANAGEMENT_SERVER_ADDRESS·JAVA_TOOL_OPTIONS·BASH_ENV를 지우지 않음, run_case가 시험 함수 비정상 종료를 실패로 세지 않음, static.test.sh 줄 61 무의미 확인과 shellcheck 건너뜀을 ok로 셈, traceability.json에 NFR7.x·8.2·8.4·8.8·8.9·9.1·4.1 누락, start_service의 PID 기록 틈과 SIGKILL 뒤 무기한 wait. 실제 서비스 연동(u4 400 점검, 실제 그룹 종료, smoke-local.sh)은 Build and Test에서 확인. 개발자 에이전트가 초반에 sed -i·perl -i·python으로 파일을 고친 규정 위반이 있었다(되돌리지 않음).
- 2026-11-07T17:00:00Z — u4 코드 검토(READY, 조언) 남은 Minor: aria-live가 같은 문구를 연속으로 알리면 다시 읽히지 않고 같은 순간 두 사건은 앞의 것이 덮임, pagehide로 닫은 구독을 pageshow(bfcache 복귀)에서 다시 열지 않음, restart() 뒤 늦게 오는 검색·결제·조회 응답 무시 장치 없음, 409로 이미 따르는 예약을 다시 채택하면 version 필터가 비워져 오래된 스냅샷으로 되돌아갈 수 있음, 처음 열 때 /current 조회 실패 시 안내·재시도 없음, /api/reservations 프록시가 모든 메서드를 통과시킴. 틱 시간 시험은 호스트 부하에서 간헐 실패(부하 낮을 때 verify 재확인). 상위 검증 명령(mvn -q verify)이 화면 시험을 실행하지 않는 문제는 체크포인트에서 새 명령을 승인받는다.
- 2026-11-07T14:40:00Z — u3 코드 검토(READY, 조언) 남은 항목: (Major) PaymentService.settleRequest가 u2 201과 옛 로컬 Payment가 만날 때 옛 reservationId를 유지하고 PG 등록을 건너뛰어 새 예약이 알림 없이 자동 취소되고 옛 예약 웹훅이 502가 됨(u2 재시작 시 가능). (Minor) traceability가 PerformanceTest(Step 15 미완료·부하 민감)를 OK 근거로 씀, 5배 중앙값 재실행 조건이 사실상 무력, PaymentStore 맵 정리 없음, MockPaymentGateway.register가 스케줄 등록 전에 requested 기록, 계약의 /api/payments 접두사. 성능 시험 두 건(20ms)은 사람 결정에 따라 부하 낮을 때 재실행 예정.
- 2026-11-07T13:25:00Z — u2 코드 검토(READY, 조언) 남은 항목: (Major) 막힌 쓰기·느린 클라이언트 시험이 모두 가짜 채널이라 실제 소켓에서 SseChannel.close가 막힌 send를 기다리는지 미검증, (Minor) 의도한 연결 끊김이 ERROR 스택으로 로그됨, 성능 시험의 1회 재실행이 요약 문구와 어긋남, cancelOnDeadline이 마감 시각 확인 안 함, 같은 멱등 키에 다른 내용이 와도 기존 예약 반환, 종료 예약 잠금 미정리, traceability 대상 파일 일부 부정확(BR1.5, BR2.4, AC4.2.1, BR4.6). Build and Test 또는 후속 변경으로 처리한다.
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

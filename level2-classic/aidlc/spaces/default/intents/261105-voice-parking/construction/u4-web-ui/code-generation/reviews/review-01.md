## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-07T16:40:55Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | services/u4-web-ui/src/composables/useApp.js > announce(), App.vue > aria-live 영역 | 안내는 문자열 ref 하나(`announcement.value = message`)를 덮어쓰는 방식이다. 같은 문구가 연속으로 나오면(예: 결제 접수 오류 `MSG_PAY_ERROR`를 두 번 연속 받음, 검색 오류 반복) DOM 텍스트가 바뀌지 않아 스크린 리더가 두 번째를 읽지 않는다. 같은 틱에 사건 두 개가 오면(예: 추천 N건 직후 다른 안내) 앞의 것이 덮어써진다. 기능 설계는 "사건 목록"을 알리라고 한다. | 같은 문구도 다시 읽히도록 안내를 비웠다가 다시 채우거나 일련번호가 있는 항목 목록으로 바꾸고, 같은 문구 연속 발생을 확인하는 시험을 추가한다. | New |
| R-02 | Minor | services/u4-web-ui/src/composables/useApp.js > onPageHide(), useReservationStream.js | `pagehide`에서 구독을 닫지만 `pageshow`(bfcache 복원)에서 다시 구독하는 코드가 없다(`pageshow`/`persisted` 검색 결과 0건). 뒤로 가기로 복원되면 결제 대기 화면이 그대로 남고 틱만 돌 뿐 푸시를 받지 못해 180초 뒤 "승인 여부를 확인하는 중" 문구에서 멈춘다. | `pageshow`에서 `persisted`이면 `/current`를 다시 읽어 `init`과 같은 경로로 복원하거나, 이 한계를 알려진 사항에 적는다. | New |
| R-03 | Minor | services/u4-web-ui/src/composables/useApp.js > pay(), submitQuery(), loadAndAdopt(), restart() | 비동기 응답에 세대 확인이 없다. 구독 쪽은 `generation`으로 늦은 결과를 버리지만, 앱 쪽에서는 `restart()`가 진행 중인 검색·결제·조회 응답을 막지 않아 새로 시작한 뒤 늦게 온 응답이 목록이나 예약 영역을 다시 채울 수 있다. 새로 시작 단추는 종료 상태에서만 보이므로 가능성은 낮다. | `restart()`에서 올리는 세대 값을 요청 시작 때 잡아 두고 응답 반영 전에 비교하거나, 가능성이 낮아 받아들인다고 기록한다. | New |
| R-04 | Minor | services/u4-web-ui/src/composables/useApp.js > handlePendingExists() → adoptReservation() | 409로 이미 따르고 있는 예약을 다시 채택하면 `stream.resetVersion`이 그 예약의 version 필터를 비운다. 이때 단건 조회가 푸시보다 오래된 스냅샷이면 낮은 version이 통과해 화면이 되돌아갈 수 있다. 새 예약이 아닌 경우에는 필터를 유지해야 예약별 version 규칙과 맞는다. | 채택하려는 예약이 현재 따르는 예약과 같으면 필터를 비우지 않는다. 이 경우 시험을 추가한다. | New |
| R-05 | Minor | services/u4-web-ui/src/composables/useApp.js > init() | `/api/reservations/current` 조회가 네트워크 오류, 5xx, 401이면 안내도 재시도도 없이 입력창 포커스만 준다. 서버에 결제 대기 예약이 있어도 S1처럼 보이고 결제 버튼이 켜진다. 결제를 누르면 409 경로가 복구하므로 치명적이지 않지만 사용자는 상태를 알 수 없다. | 조회 실패 시 `MSG_RESERVATION_LOAD_FAILED` 안내를 보이거나, 의도된 조용한 처리라고 설계에 적는다. | New |
| R-06 | Minor | services/u4-web-ui/vite.proxy.js > createProxyConfig() `/api/reservations` | 이 키는 접두어 일치라 모든 메서드와 하위 경로를 u2로 보낸다. 화면이 쓰는 것은 GET(단건, current, events)뿐이다. u2가 이 접두어 아래에 상태를 바꾸는 공개 경로를 두면 개발 서버가 그대로 통과시킨다. 계약상 `/internal/**`은 연결하지 않아 서명 검증이 필요한 웹훅은 안전하다(통합 시험 확인). | `/api/reservations`에도 GET만 통과시키는 bypass를 걸거나, 접두어 전체 통과가 의도라고 기록한다. | New |
| R-07 | Minor | services/u4-web-ui/test/ui.timing.test.js > '틱 한 번의 갱신이 p99 5ms 이하' | 이 시험은 가짜 타이머로 한 틱을 직접 돌려 Vue 갱신 시간을 잰다. 판정은 100표본의 p99(둘째로 큰 값)와 최댓값 20ms다. 코드 요약은 호스트 부하에서 4회 중 2회, 10회 중 1회 실패했다고 밝힌다(정직함). 따라서 이 유닛은 아직 `verify` 전체 통과 실행을 갖지 못했다. 목표와 통계는 낮추지 않았고 재실행으로 가리지도 않았다(`judge`, `dropWarmup` 확인). | Unit 체크포인트에서 부하가 낮을 때 `verify`가 통과하는 실행을 사람이 확인한다. 반복되면 시험이 재는 구간(콜백 실행)과 호스트 정지가 섞이지 않게 재거나 측정 방식을 사람이 정한다. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `npm --prefix services/u4-web-ui run test:unit` | PASS: 17 files, 351 tests passed | 코드 요약의 351과 일치한다. |
| `npm --prefix services/u4-web-ui run lint` | PASS: eslint, prettier 모두 통과 | 금지 API 규칙과 포맷이 통과한다. |
| `npm --prefix services/u4-web-ui run build` | PASS: 25 modules, `dist/assets/index-707mmqdg.js` 86.05 kB | 빌드가 성공한다. |
| `npm --prefix services/u4-web-ui run test:coverage` | PASS: 라인 98.29% (520/529), 구문 98.39%, 분기 93.9%, 함수 96.71% | 하한 80%를 넘고 코드 요약의 수치와 정확히 같다. |
| `verify`, `test`(timing·proxy 프로젝트) | 실행하지 않음 (지시에 따라). timing 시험은 소스를 읽어 평가함 | 틱 시험의 간헐 실패는 코드 요약이 이미 공개했다. 위 Minor 참조. |

### Summary

기능 설계 W1~W5와 결정 F1~F14는 코드에서 확인된다: 두 영역 상태, 200 응답 재생 처리(PENDING이면 구독, 종료 상태면 단건 조회), 시도 `{passId, durationHours, key}`의 400·401 정리, 예약별 version 필터, 종료 시 구독 닫기, CLOSED 복구(1·2·5초, 60초 한도, 404는 S1, 401은 안내)가 모두 구현되고 시험으로 지켜진다. 보안 쪽도 `v-html` 등 금지 API 0건, 세션 ID는 헤더와 `buildEventsUrl` 한 곳에만 있고, 결제 정확 일치 프록시·웹훅·`/internal` 차단이 실제 Vite `createServer` 통합 시험으로 확인되며, 루프백 검사는 `configResolved`와 `strictPort`로 걸린다. 차단 사유가 되는 Critical이나 Major는 없고, 남는 것은 접근성 재알림, bfcache 복원, 늦은 응답 처리 등 Minor이며 `traceability.json`의 OK 대상 파일은 실제로 구현·시험하는 파일을 가리키고 `code-summary.md`는 코드와 일치하며 NFR7.2의 보류도 분명히 적혀 있다.

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApi } from '../api/api.js'
import { useApp } from './useApp.js'
import { createSession } from './useSession.js'
import {
  FakeEventSource,
  createFakeFetch,
  createMemoryStorage,
  deferred,
  hang,
  makeAccepted,
  makeQueryResponse,
  makeReservation,
  networkFailure,
  reply,
  threeRecommendations,
} from '../../test/support/fakes.js'
import {
  MSG_EMPTY,
  MSG_MISSING_DURATION,
  MSG_MISSING_PLACE,
  MSG_PASS_UNAVAILABLE,
  MSG_PAY_ERROR,
  MSG_REASSIGN_HINT,
  MSG_RESERVATION_LOAD_FAILED,
  MSG_SEARCH_ERROR,
  MSG_SESSION_UNKNOWN,
  MSG_STREAM_UNSTABLE,
  MSG_WAITING_CONFIRM,
} from '../lib/messages.js'

const START = Date.parse('2026-11-08T01:00:00.000Z')
const SEARCH = 'POST /api/search/recommendations'
const PAY = 'POST /api/payments'
const CURRENT = 'GET /api/reservations/current'

let ctx

function setup() {
  FakeEventSource.reset()
  const fetchFn = createFakeFetch()
  const storage = createMemoryStorage()
  const session = createSession({ getStorage: () => storage })
  const api = createApi({ getSessionId: session.getSessionId, fetchFn })
  const clock = { ms: START }
  const target = new EventTarget()
  let keyCounter = 0
  const app = useApp({
    api,
    session,
    EventSource: FakeEventSource,
    now: () => clock.ms,
    uuid: () => `key-${++keyCounter}`,
    target,
  })
  return { app, fetchFn, storage, session, clock, target }
}

async function showResults(recommendations = threeRecommendations(), duration = 2) {
  ctx.fetchFn.once(SEARCH, reply(200, makeQueryResponse(recommendations, duration)))
  ctx.app.text.value = '강남역 근처 2시간 주차권 찾아줘'
  await ctx.app.submitQuery()
}

async function startPending(passId = 'p1', accepted = makeAccepted()) {
  await showResults()
  ctx.fetchFn.once(PAY, reply(202, accepted))
  await ctx.app.pay(passId)
}

async function tick(seconds) {
  for (let i = 0; i < seconds; i += 1) {
    ctx.clock.ms += 1000
    await vi.advanceTimersByTimeAsync(1000)
  }
}

beforeEach(() => {
  vi.useFakeTimers()
  ctx = setup()
})

afterEach(() => {
  ctx.app.dispose()
  vi.useRealTimers()
})

describe('추천 요청 (W2)', () => {
  it('요청 중에는 로딩이고 전송이 막히며, 후보가 있으면 결과(S3)를 보인다', async () => {
    const response = deferred()
    ctx.fetchFn.once(SEARCH, response.handler)
    ctx.app.text.value = '  강남역 근처 2시간  '
    expect(ctx.app.canSubmit.value).toBe(true)

    const pending = ctx.app.submitQuery()
    expect(ctx.app.loading.value).toBe(true)
    expect(ctx.app.canSubmit.value).toBe(false)

    response.resolve(200, makeQueryResponse(threeRecommendations()))
    await pending

    expect(ctx.app.loading.value).toBe(false)
    expect(ctx.app.resultView.value).toBe('results')
    expect(ctx.app.recommendations.value).toHaveLength(3)
    expect(ctx.app.query.value.durationHours).toBe(2)
    expect(ctx.app.announcement.value).toBe('추천 3건')
    expect(ctx.fetchFn.bodyOf(ctx.fetchFn.calls[0])).toEqual({ text: '강남역 근처 2시간' })
    expect(ctx.session.loadRecommendations()).not.toBeNull()
  })

  it('요청 중 중복 전송은 한 번만 보낸다', async () => {
    const response = deferred()
    ctx.fetchFn.once(SEARCH, response.handler)
    ctx.app.text.value = '강남역 2시간'
    const first = ctx.app.submitQuery()
    await ctx.app.submitQuery()
    response.resolve(200, makeQueryResponse([]))
    await first
    expect(ctx.fetchFn.callsFor(SEARCH)).toHaveLength(1)
  })

  it('후보가 없으면 S4 안내를 보이고 이전 목록과 저장된 목록을 비운다', async () => {
    await showResults()
    ctx.fetchFn.once(SEARCH, reply(200, makeQueryResponse([])))
    await ctx.app.submitQuery()
    expect(ctx.app.resultView.value).toBe('empty')
    expect(ctx.app.recommendations.value).toEqual([])
    expect(ctx.app.announcement.value).toBe(MSG_EMPTY)
    expect(ctx.session.loadRecommendations()).toBeNull()
  })

  it.each([
    ['MISSING_PLACE', MSG_MISSING_PLACE],
    ['MISSING_DURATION', MSG_MISSING_DURATION],
  ])('422 %s는 해석 오류(S2)이고 이전 목록을 비운다', async (code, message) => {
    await showResults()
    ctx.fetchFn.once(SEARCH, reply(422, { code, message: '서버 문구' }))
    await ctx.app.submitQuery()
    expect(ctx.app.resultView.value).toBe('interpretError')
    expect(ctx.app.queryError.value).toBe(message)
    expect(ctx.app.recommendations.value).toEqual([])
    expect(ctx.app.announcement.value).toBe(message)
    expect(ctx.session.loadRecommendations()).toBeNull()
  })

  it.each([
    ['네트워크 오류', networkFailure()],
    ['500', reply(500, { code: 'INTERNAL_ERROR', message: 'x' })],
    ['400', reply(400, { code: 'INVALID_REQUEST', message: 'x' })],
    ['형식이 틀린 200 본문', reply(200, { query: {} })],
    ['알 수 없는 422 코드', reply(422, { code: 'SOMETHING', message: 'x' })],
  ])('%s이면 안내를 보이고 이전 결과를 유지한다', async (_label, handler) => {
    await showResults()
    ctx.fetchFn.once(SEARCH, handler)
    await ctx.app.submitQuery()
    expect(ctx.app.queryError.value).toBe(MSG_SEARCH_ERROR)
    expect(ctx.app.resultView.value).toBe('results')
    expect(ctx.app.recommendations.value).toHaveLength(3)
    expect(ctx.app.loading.value).toBe(false)
    expect(ctx.app.announcement.value).toBe(MSG_SEARCH_ERROR)
  })

  it('3초 안에 응답이 없으면 같은 안내를 보이고 다시 전송할 수 있다', async () => {
    ctx.fetchFn.once(SEARCH, hang())
    ctx.app.text.value = '강남역 2시간'
    const pending = ctx.app.submitQuery()
    await vi.advanceTimersByTimeAsync(3000)
    await pending
    expect(ctx.app.queryError.value).toBe(MSG_SEARCH_ERROR)
    expect(ctx.app.canSubmit.value).toBe(true)

    ctx.fetchFn.once(SEARCH, reply(200, makeQueryResponse(threeRecommendations())))
    await ctx.app.submitQuery()
    expect(ctx.app.queryError.value).toBe('')
    expect(ctx.app.resultView.value).toBe('results')
  })

  it('공백뿐이거나 200자를 넘는 입력은 전송하지 않는다', async () => {
    ctx.app.text.value = '   '
    expect(ctx.app.canSubmit.value).toBe(false)
    await ctx.app.submitQuery()
    ctx.app.text.value = 'ㄱ'.repeat(201)
    expect(ctx.app.canSubmit.value).toBe(false)
    await ctx.app.submitQuery()
    expect(ctx.fetchFn.calls).toHaveLength(0)
    ctx.app.text.value = 'ㄱ'.repeat(200)
    expect(ctx.app.canSubmit.value).toBe(true)
  })

  it('결제 대기 중에도 새 검색은 되지만 결제 버튼은 계속 막힌다', async () => {
    await startPending()
    ctx.fetchFn.once(SEARCH, reply(200, makeQueryResponse(threeRecommendations(), 3)))
    await ctx.app.submitQuery()
    expect(ctx.app.resultView.value).toBe('results')
    expect(ctx.app.reservationView.value).toBe('pending')
    expect(ctx.app.payDisabled.value).toBe(true)
  })

  it('422여도 예약 상태 영역은 건드리지 않는다', async () => {
    await startPending()
    ctx.fetchFn.once(SEARCH, reply(422, { code: 'MISSING_PLACE', message: 'x' }))
    await ctx.app.submitQuery()
    expect(ctx.app.reservationView.value).toBe('pending')
  })
})

describe('결제 요청 (W3)', () => {
  it('202이면 결제 대기(S5)로 바꾸고 해당 예약을 구독한다', async () => {
    await startPending()
    const [call] = ctx.fetchFn.callsFor(PAY)
    expect(ctx.fetchFn.bodyOf(call)).toEqual({
      passId: 'p1',
      durationHours: 2,
      idempotencyKey: 'key-1',
      mockScenario: 'APPROVE',
      mockDelaySeconds: 0,
    })
    expect(ctx.app.reservationView.value).toBe('pending')
    expect(ctx.app.reservation.value).toMatchObject({
      reservationId: 'r1',
      lotName: '강남역 공영주차장',
      durationHours: 2,
      amount: 6000,
    })
    expect(FakeEventSource.open).toHaveLength(1)
    expect(FakeEventSource.open[0].url).toContain('/api/reservations/r1/events?sessionId=')
    expect(ctx.app.lastPaidPassId.value).toBe('p1')
    expect(ctx.session.loadLastPaidPassId()).toBe('p1')
    expect(ctx.app.paying.value).toBe(false)
    expect(ctx.app.payDisabled.value).toBe(true)
    expect(ctx.app.focusRequest.value.target).toBe('status')
  })

  it('요청 중에는 결제 버튼이 막히고 중복 결제는 보내지 않는다', async () => {
    await showResults()
    const response = deferred()
    ctx.fetchFn.once(PAY, response.handler)
    const pending = ctx.app.pay('p1')
    expect(ctx.app.paying.value).toBe(true)
    expect(ctx.app.payDisabled.value).toBe(true)
    await ctx.app.pay('p2')
    response.resolve(202, makeAccepted())
    await pending
    expect(ctx.fetchFn.callsFor(PAY)).toHaveLength(1)
  })

  it('결제 대기 중에는 다른 결제를 보내지 않는다', async () => {
    await startPending()
    await ctx.app.pay('p2')
    expect(ctx.fetchFn.callsFor(PAY)).toHaveLength(1)
  })

  it('200 + PENDING_PAYMENT도 202처럼 결제 대기와 구독이 된다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, reply(200, makeAccepted()))
    await ctx.app.pay('p1')
    expect(ctx.app.reservationView.value).toBe('pending')
    expect(FakeEventSource.open).toHaveLength(1)
    expect(ctx.app.lastPaidPassId.value).toBe('p1')
  })

  it('200 + 종료 상태이면 단건 조회로 채워 S6을 보이고 구독하지 않는다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, reply(200, makeAccepted({ status: 'CONFIRMED' })))
    ctx.fetchFn.once(
      'GET /api/reservations/r1',
      reply(
        200,
        makeReservation({ version: 2, status: 'CONFIRMED', message: '예약이 완료되었습니다' }),
      ),
    )
    await ctx.app.pay('p1')
    expect(ctx.app.reservationView.value).toBe('confirmed')
    expect(ctx.app.reservation.value).toMatchObject({ lotName: '강남역 공영주차장', amount: 6000 })
    expect(FakeEventSource.instances).toHaveLength(0)
    expect(ctx.app.lastPaidPassId.value).toBe('p1')
  })

  it('200 + 종료 상태의 조회가 실패하면 새로 고침 안내를 보이고 재시도하지 않는다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, reply(200, makeAccepted({ status: 'PAYMENT_FAILED' })))
    ctx.fetchFn.once('GET /api/reservations/r1', reply(500, undefined))
    await ctx.app.pay('p1')
    await vi.advanceTimersByTimeAsync(60000)
    expect(ctx.app.reservationNotice.value).toBe(MSG_RESERVATION_LOAD_FAILED)
    expect(ctx.app.announcement.value).toBe(MSG_RESERVATION_LOAD_FAILED)
    expect(ctx.app.reservationView.value).toBe('none')
    expect(ctx.fetchFn.callsFor('GET /api/reservations/r1')).toHaveLength(1)
  })

  it('조회가 404이면 예약 영역을 비우고 안내 없이 S1로 간다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, reply(200, makeAccepted({ status: 'CANCELLED' })))
    ctx.fetchFn.once('GET /api/reservations/r1', reply(404, { code: 'NOT_FOUND', message: 'x' }))
    await ctx.app.pay('p1')
    expect(ctx.app.reservationView.value).toBe('none')
    expect(ctx.app.reservationNotice.value).toBe('')
  })

  it('409 PENDING_EXISTS이면 기존 예약으로 결제 대기를 보이고 passId는 기록하지 않는다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, reply(409, { code: 'PENDING_EXISTS', message: 'x', reservationId: 'r9' }))
    ctx.fetchFn.once(
      'GET /api/reservations/r9',
      reply(200, makeReservation({ reservationId: 'r9', version: 3 })),
    )
    await ctx.app.pay('p2')
    expect(ctx.app.reservationView.value).toBe('pending')
    expect(ctx.app.reservation.value.reservationId).toBe('r9')
    expect(FakeEventSource.open[0].url).toContain('/r9/events')
    expect(ctx.app.lastPaidPassId.value).toBeNull()
  })

  it('409에 reservationId가 없으면 현재 예약 조회로 대신한다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, reply(409, { code: 'PENDING_EXISTS', message: 'x' }))
    ctx.fetchFn.once(CURRENT, reply(200, makeReservation({ reservationId: 'r7' })))
    await ctx.app.pay('p1')
    expect(ctx.app.reservation.value.reservationId).toBe('r7')
  })

  it.each([
    ['404 PASS_NOT_FOUND', reply(404, { code: 'PASS_NOT_FOUND', message: 'x' })],
    ['422 DURATION_NOT_AVAILABLE', reply(422, { code: 'DURATION_NOT_AVAILABLE', message: 'x' })],
  ])('%s이면 사용 불가 안내를 보이고 목록은 유지한다', async (_label, handler) => {
    await showResults()
    ctx.fetchFn.once(PAY, handler)
    await ctx.app.pay('p1')
    expect(ctx.app.payNotice.value).toBe(MSG_PASS_UNAVAILABLE)
    expect(ctx.app.announcement.value).toBe(MSG_PASS_UNAVAILABLE)
    expect(ctx.app.recommendations.value).toHaveLength(3)
    expect(ctx.app.reservationView.value).toBe('none')
    expect(ctx.app.paying.value).toBe(false)
  })

  it.each([
    ['502', reply(502, { code: 'UPSTREAM_FAILED', message: 'x' })],
    ['500', reply(500, undefined)],
    ['네트워크 오류', networkFailure()],
    ['400', reply(400, { code: 'INVALID_REQUEST', message: 'x' })],
    ['401', reply(401, { code: 'MISSING_SESSION', message: 'x' })],
  ])('%s이면 접수 오류 안내를 보인다', async (_label, handler) => {
    await showResults()
    ctx.fetchFn.once(PAY, handler)
    await ctx.app.pay('p1')
    expect(ctx.app.payNotice.value).toBe(MSG_PAY_ERROR)
    expect(ctx.app.announcement.value).toBe(MSG_PAY_ERROR)
    expect(ctx.app.reservationView.value).toBe('none')
    expect(ctx.app.paying.value).toBe(false)
  })

  it('3초 안에 응답이 없으면 접수 오류 안내를 보인다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, hang())
    const pending = ctx.app.pay('p1')
    await vi.advanceTimersByTimeAsync(3000)
    await pending
    expect(ctx.app.payNotice.value).toBe(MSG_PAY_ERROR)
  })

  it('다음 결제 시도 때 이전 결제 안내를 지운다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    expect(ctx.app.payNotice.value).not.toBe('')
    ctx.fetchFn.once(PAY, reply(202, makeAccepted()))
    await ctx.app.pay('p1')
    expect(ctx.app.payNotice.value).toBe('')
  })

  it('추천 목록이 없으면 결제하지 않는다', async () => {
    await ctx.app.pay('p1')
    expect(ctx.fetchFn.calls).toHaveLength(0)
  })
})

describe('시연 설정 (UR6.1)', () => {
  it('지연 시나리오는 지연 초를 보내고 그 밖에는 0을 보낸다', async () => {
    await showResults()
    ctx.app.setScenario('DELAY')
    ctx.app.setDelaySeconds(30)
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    expect(ctx.fetchFn.bodyOf(ctx.fetchFn.callsFor(PAY)[0])).toMatchObject({
      mockScenario: 'DELAY',
      mockDelaySeconds: 30,
    })

    ctx.app.setScenario('FAIL')
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    expect(ctx.fetchFn.bodyOf(ctx.fetchFn.callsFor(PAY)[1])).toMatchObject({
      mockScenario: 'FAIL',
      mockDelaySeconds: 0,
    })
  })

  it('지연 초는 1~600으로 맞추고 기본값은 200이다', () => {
    expect(ctx.app.scenario.value).toBe('APPROVE')
    expect(ctx.app.delaySeconds.value).toBe(200)
    ctx.app.setDelaySeconds(0)
    expect(ctx.app.delaySeconds.value).toBe(1)
    ctx.app.setDelaySeconds(99999)
    expect(ctx.app.delaySeconds.value).toBe(600)
    ctx.app.setDelaySeconds('abc')
    expect(ctx.app.delaySeconds.value).toBe(200)
  })

  it('알 수 없는 시나리오는 승인으로 돌린다', () => {
    ctx.app.setScenario('HACK')
    expect(ctx.app.scenario.value).toBe('APPROVE')
  })
})

describe('멱등 키 (F8, NFR5.1)', () => {
  const keyOf = (index) => ctx.fetchFn.bodyOf(ctx.fetchFn.callsFor(PAY)[index]).idempotencyKey

  it('응답을 받지 못한 같은 후보 재시도는 같은 키를 쓴다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    ctx.fetchFn.once(PAY, networkFailure())
    await ctx.app.pay('p1')
    ctx.fetchFn.once(PAY, hang())
    const timedOut = ctx.app.pay('p1')
    await vi.advanceTimersByTimeAsync(3000)
    await timedOut
    ctx.fetchFn.once(PAY, reply(202, makeAccepted()))
    await ctx.app.pay('p1')
    expect([keyOf(0), keyOf(1), keyOf(2), keyOf(3)]).toEqual(['key-1', 'key-1', 'key-1', 'key-1'])
  })

  it('다른 후보를 누르면 새 키를 쓰고, 원래 후보로 돌아와도 새 키다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p2')
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    expect([keyOf(0), keyOf(1), keyOf(2)]).toEqual(['key-1', 'key-2', 'key-3'])
  })

  it('같은 후보여도 이용 시간이 다르면 새 키를 쓴다', async () => {
    await showResults(threeRecommendations(), 2)
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    await showResults(threeRecommendations(), 3)
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    expect(keyOf(0)).toBe('key-1')
    expect(keyOf(1)).toBe('key-2')
    expect(ctx.fetchFn.bodyOf(ctx.fetchFn.callsFor(PAY)[1]).durationHours).toBe(3)
  })

  it.each([
    ['404', reply(404, { code: 'PASS_NOT_FOUND', message: 'x' })],
    ['422', reply(422, { code: 'DURATION_NOT_AVAILABLE', message: 'x' })],
    ['400', reply(400, { code: 'INVALID_REQUEST', message: 'x' })],
    ['401', reply(401, { code: 'MISSING_SESSION', message: 'x' })],
  ])('확정된 %s 응답 뒤에는 새 키를 쓴다', async (_label, handler) => {
    await showResults()
    ctx.fetchFn.once(PAY, handler)
    await ctx.app.pay('p1')
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    expect([keyOf(0), keyOf(1)]).toEqual(['key-1', 'key-2'])
  })

  it('409 뒤에는 새 키를 쓴다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, reply(409, { code: 'PENDING_EXISTS', message: 'x', reservationId: 'r9' }))
    ctx.fetchFn.once('GET /api/reservations/r9', reply(404, { code: 'NOT_FOUND', message: 'x' }))
    await ctx.app.pay('p1')
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    expect([keyOf(0), keyOf(1)]).toEqual(['key-1', 'key-2'])
  })

  it('202 뒤에는 새 키를 쓴다', async () => {
    await startPending()
    FakeEventSource.instances[0].snapshot(makeReservation({ version: 2, status: 'CANCELLED' }))
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p2')
    expect([keyOf(0), keyOf(1)]).toEqual(['key-1', 'key-2'])
  })

  it('새로 시작하면 보관된 시도를 지운다', async () => {
    await showResults()
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    ctx.app.restart()
    await showResults()
    ctx.fetchFn.once(PAY, reply(502, undefined))
    await ctx.app.pay('p1')
    expect([keyOf(0), keyOf(1)]).toEqual(['key-1', 'key-2'])
  })
})

describe('실시간 갱신 (W4)', () => {
  it('CONFIRMED는 S6으로 바꾸고 구독을 닫고 안내를 알린다', async () => {
    await startPending()
    const seqBefore = ctx.app.focusRequest.value.seq
    FakeEventSource.instances[0].snapshot(
      makeReservation({ version: 2, status: 'CONFIRMED', message: '예약이 완료되었습니다' }),
    )
    expect(ctx.app.reservationView.value).toBe('confirmed')
    expect(ctx.app.reservation.value.lotName).toBe('강남역 공영주차장')
    expect(ctx.app.announcement.value).toBe('예약이 완료되었습니다')
    expect(FakeEventSource.open).toHaveLength(0)
    expect(ctx.app.focusRequest.value.target).toBe('status')
    expect(ctx.app.focusRequest.value.seq).toBeGreaterThan(seqBefore)
  })

  it('PAYMENT_FAILED는 S7이고 마지막 결제 후보가 목록에 있으면 그 카드를 실패로 표시한다', async () => {
    await startPending('p2', makeAccepted())
    FakeEventSource.instances[0].snapshot(
      makeReservation({ version: 2, status: 'PAYMENT_FAILED', message: '결제가 거절되었습니다' }),
    )
    expect(ctx.app.reservationView.value).toBe('failed')
    expect(ctx.app.failedPassId.value).toBe('p2')
    expect(ctx.app.announcement.value).toContain('결제가 거절되었습니다')
    expect(ctx.app.announcement.value).toContain(MSG_REASSIGN_HINT)
  })

  it('CANCELLED는 S8이다', async () => {
    await startPending()
    FakeEventSource.instances[0].snapshot(
      makeReservation({
        version: 2,
        status: 'CANCELLED',
        message: '승인 지연으로 예약이 취소되었습니다.',
      }),
    )
    expect(ctx.app.reservationView.value).toBe('cancelled')
    expect(ctx.app.announcement.value).toBe('승인 지연으로 예약이 취소되었습니다.')
  })

  it('실패 뒤 다른 후보를 새 키로 결제하면 새 예약의 version 1이 반영된다', async () => {
    await startPending('p1')
    FakeEventSource.instances[0].snapshot(makeReservation({ version: 2, status: 'PAYMENT_FAILED' }))
    ctx.fetchFn.once(PAY, reply(202, makeAccepted({ paymentId: 'pay2', reservationId: 'r2' })))
    await ctx.app.pay('p2')
    expect(ctx.app.reservationView.value).toBe('pending')
    expect(ctx.app.failedPassId.value).toBeNull()
    const second = FakeEventSource.instances[1]
    expect(second.url).toContain('/r2/events')
    second.snapshot(
      makeReservation({ reservationId: 'r2', version: 1, message: '둘째 예약 대기', lotName: 'B' }),
    )
    expect(ctx.app.reservation.value).toMatchObject({ reservationId: 'r2', lotName: 'B' })
    second.snapshot(makeReservation({ reservationId: 'r2', version: 2, status: 'CONFIRMED' }))
    expect(ctx.app.reservationView.value).toBe('confirmed')
    expect(ctx.fetchFn.bodyOf(ctx.fetchFn.callsFor(PAY)[1]).idempotencyKey).toBe('key-2')
  })

  it('낡은 version의 스냅샷은 화면을 바꾸지 않는다', async () => {
    await startPending()
    const [source] = FakeEventSource.instances
    source.snapshot(makeReservation({ version: 2, message: '둘' }))
    source.snapshot(makeReservation({ version: 1, message: '하나' }))
    expect(ctx.app.reservation.value.message).toBe('둘')
  })

  it('실패 카드는 현재 예약이 failed이고 목록에 그 passId가 있을 때만 표시한다 (F12)', async () => {
    await startPending('p1')
    FakeEventSource.instances[0].snapshot(makeReservation({ version: 2, status: 'PAYMENT_FAILED' }))
    expect(ctx.app.failedPassId.value).toBe('p1')
    ctx.fetchFn.once(SEARCH, reply(200, makeQueryResponse([threeRecommendations()[1]])))
    await ctx.app.submitQuery()
    expect(ctx.app.failedPassId.value).toBeNull()
  })

  it('pagehide에서 구독을 닫는다', async () => {
    await startPending()
    ctx.target.dispatchEvent(new Event('pagehide'))
    expect(FakeEventSource.open).toHaveLength(0)
  })

  it('dispose하면 pagehide 처리기와 틱을 거둔다', async () => {
    await startPending()
    ctx.app.dispose()
    expect(FakeEventSource.open).toHaveLength(0)
    expect(vi.getTimerCount()).toBe(0)
  })
})

describe('연결 복구 연동 (UR4.4)', () => {
  it('조회가 404이면 예약 영역을 비운다', async () => {
    await startPending()
    ctx.fetchFn.once('GET /api/reservations/r1', reply(404, { code: 'NOT_FOUND', message: 'x' }))
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    expect(ctx.app.reservationView.value).toBe('none')
    expect(vi.getTimerCount()).toBe(0)
  })

  it('조회가 401이면 세션 안내를 보이고 예약 영역은 유지한다', async () => {
    await startPending()
    ctx.fetchFn.once(
      'GET /api/reservations/r1',
      reply(401, { code: 'MISSING_SESSION', message: 'x' }),
    )
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    expect(ctx.app.reservationNotice.value).toBe(MSG_SESSION_UNKNOWN)
    expect(ctx.app.announcement.value).toBe(MSG_SESSION_UNKNOWN)
    expect(ctx.app.reservationView.value).toBe('pending')
  })

  it('60초 안에 복구되지 않으면 새로 고침 안내를 보이고 마지막 상태를 유지한다', async () => {
    await startPending()
    ctx.fetchFn.on('GET /api/reservations/r1', networkFailure())
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(60000)
    expect(ctx.app.reservationNotice.value).toBe(MSG_STREAM_UNSTABLE)
    expect(ctx.app.reservationView.value).toBe('pending')
  })

  it('복구 중에 새로 시작하면 복구를 멈춘다', async () => {
    await startPending()
    ctx.fetchFn.on('GET /api/reservations/r1', networkFailure())
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    ctx.app.restart()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('새로 시작하면 복구 안내를 지운다', async () => {
    await startPending()
    ctx.fetchFn.once(
      'GET /api/reservations/r1',
      reply(401, { code: 'MISSING_SESSION', message: 'x' }),
    )
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    expect(ctx.app.reservationNotice.value).not.toBe('')
    ctx.app.restart()
    expect(ctx.app.reservationNotice.value).toBe('')
  })
})

describe('경과 시간 (UR4.5)', () => {
  it('결제 요청 시각부터 지금까지의 초를 1초마다 갱신한다', async () => {
    ctx.clock.ms = START + 42000
    await startPending('p1', makeAccepted({ paymentRequestedAt: new Date(START).toISOString() }))
    expect(ctx.app.elapsedSeconds.value).toBe(42)
    await tick(1)
    expect(ctx.app.elapsedSeconds.value).toBe(43)
    await tick(2)
    expect(ctx.app.elapsedSeconds.value).toBe(45)
  })

  it('시계가 어긋나 음수가 되면 0으로 맞춘다', async () => {
    ctx.clock.ms = START - 5000
    await startPending()
    expect(ctx.app.elapsedSeconds.value).toBe(0)
  })

  it('180초에 닿으면 확인 중 문구 상태가 되고 화면이 스스로 취소하지 않는다', async () => {
    await startPending()
    await tick(179)
    expect(ctx.app.elapsedSeconds.value).toBe(179)
    expect(ctx.app.waitingConfirm.value).toBe(false)
    await tick(1)
    expect(ctx.app.elapsedSeconds.value).toBe(180)
    expect(ctx.app.waitingConfirm.value).toBe(true)
    expect(MSG_WAITING_CONFIRM).toBe('승인 여부를 확인하는 중입니다...')
    await tick(30)
    expect(ctx.app.elapsedSeconds.value).toBe(180)
    expect(ctx.app.reservationView.value).toBe('pending')
  })

  it('결제 대기가 끝나면 틱을 해제한다', async () => {
    await startPending()
    expect(vi.getTimerCount()).toBeGreaterThan(0)
    FakeEventSource.instances[0].snapshot(makeReservation({ version: 2, status: 'CONFIRMED' }))
    expect(vi.getTimerCount()).toBe(0)
    expect(ctx.app.waitingConfirm.value).toBe(false)
  })

  it('결제 요청 시각을 해석할 수 없으면 0초로 둔다', async () => {
    await startPending('p1', makeAccepted({ paymentRequestedAt: 'not-a-date' }))
    expect(ctx.app.elapsedSeconds.value).toBe(0)
  })
})

describe('처음 열기 (W1)', () => {
  it('현재 예약이 없으면(404) 입력 대기이고 입력창에 포커스를 둔다', async () => {
    ctx.fetchFn.once(CURRENT, reply(404, { code: 'NO_RESERVATION', message: 'x' }))
    await ctx.app.init()
    expect(ctx.app.reservationView.value).toBe('none')
    expect(ctx.app.resultView.value).toBe('idle')
    expect(ctx.app.focusRequest.value.target).toBe('input')
    expect(FakeEventSource.instances).toHaveLength(0)
  })

  it('결제 대기 예약이면 S5와 구독을 복원하고 상태 제목으로 포커스를 옮기지 않는다', async () => {
    ctx.clock.ms = START + 30000
    ctx.fetchFn.once(CURRENT, reply(200, makeReservation({ version: 2, reservationId: 'r5' })))
    await ctx.app.init()
    expect(ctx.app.reservationView.value).toBe('pending')
    expect(ctx.app.elapsedSeconds.value).toBe(30)
    expect(FakeEventSource.open).toHaveLength(1)
    expect(FakeEventSource.open[0].url).toContain('/r5/events')
    expect(ctx.app.focusRequest.value.target).toBe('input')
    // 복원에서 이미 본 version의 스냅샷이 구독으로 다시 와도 중복 반영되지 않는다
    FakeEventSource.open[0].snapshot(
      makeReservation({ reservationId: 'r5', version: 2, message: '중복' }),
    )
    expect(ctx.app.reservation.value.message).not.toBe('중복')
  })

  it('종료된 예약은 구독 없이 해당 상태를 보인다', async () => {
    ctx.fetchFn.once(
      CURRENT,
      reply(
        200,
        makeReservation({ version: 3, status: 'CONFIRMED', message: '예약이 완료되었습니다' }),
      ),
    )
    await ctx.app.init()
    expect(ctx.app.reservationView.value).toBe('confirmed')
    expect(FakeEventSource.instances).toHaveLength(0)
  })

  it('저장된 추천 목록을 복원하고, 실패 예약이면 저장된 passId 카드를 실패로 보인다', async () => {
    ctx.session.saveRecommendations(makeQueryResponse(threeRecommendations()))
    ctx.session.saveLastPaidPassId('p2')
    ctx.fetchFn.once(CURRENT, reply(200, makeReservation({ version: 3, status: 'PAYMENT_FAILED' })))
    await ctx.app.init()
    expect(ctx.app.resultView.value).toBe('results')
    expect(ctx.app.recommendations.value).toHaveLength(3)
    expect(ctx.app.hasCards.value).toBe(true)
    expect(ctx.app.failedPassId.value).toBe('p2')
  })

  it('실패 예약인데 추천 목록이 없으면 카드가 없음을 알 수 있다 (S7에서 새로 검색하기)', async () => {
    ctx.fetchFn.once(CURRENT, reply(200, makeReservation({ version: 3, status: 'PAYMENT_FAILED' })))
    await ctx.app.init()
    expect(ctx.app.reservationView.value).toBe('failed')
    expect(ctx.app.hasCards.value).toBe(false)
    expect(ctx.app.failedPassId.value).toBeNull()
  })

  it.each([
    ['네트워크 오류', networkFailure()],
    ['500', reply(500, undefined)],
    ['형식이 틀린 본문', reply(200, { hello: 'world' })],
  ])('현재 예약 조회가 %s이면 안내 없이 입력 대기로 시작한다', async (_label, handler) => {
    ctx.fetchFn.once(CURRENT, handler)
    await ctx.app.init()
    expect(ctx.app.reservationView.value).toBe('none')
    expect(ctx.app.reservationNotice.value).toBe('')
  })
})

describe('새로 시작 (W5)', () => {
  it('두 영역, 구독, 저장된 값, 입력, 시도를 비우고 세션 ID는 유지한다', async () => {
    await startPending()
    FakeEventSource.instances[0].snapshot(makeReservation({ version: 2, status: 'CONFIRMED' }))
    const sessionId = ctx.session.getSessionId()
    ctx.app.restart()
    expect(ctx.app.resultView.value).toBe('idle')
    expect(ctx.app.reservationView.value).toBe('none')
    expect(ctx.app.recommendations.value).toEqual([])
    expect(ctx.app.query.value).toBeNull()
    expect(ctx.app.text.value).toBe('')
    expect(ctx.app.lastPaidPassId.value).toBeNull()
    expect(ctx.app.payNotice.value).toBe('')
    expect(ctx.app.queryError.value).toBe('')
    expect(ctx.session.loadRecommendations()).toBeNull()
    expect(ctx.session.loadLastPaidPassId()).toBeNull()
    expect(ctx.session.getSessionId()).toBe(sessionId)
    expect(ctx.app.focusRequest.value.target).toBe('input')
  })

  it('결제 대기 중에 새로 시작하면 구독과 틱을 멈춘다', async () => {
    await startPending()
    ctx.app.restart()
    expect(FakeEventSource.open).toHaveLength(0)
    expect(vi.getTimerCount()).toBe(0)
  })
})

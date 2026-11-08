import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildEventsUrl, createApi, REQUEST_TIMEOUT_MS } from './api.js'
import {
  SESSION_UUID,
  createFakeFetch,
  flushMicrotasks,
  hang,
  makeReservation,
  networkFailure,
  reply,
} from '../../test/support/fakes.js'

function setup(overrides = {}) {
  const fetchFn = createFakeFetch()
  const api = createApi({ getSessionId: () => SESSION_UUID, fetchFn, ...overrides })
  return { fetchFn, api }
}

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('요청 형태', () => {
  it('recommend는 상대 경로로 POST하고 X-Session-Id와 JSON 본문을 보낸다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on('POST /api/search/recommendations', reply(200, { query: {}, recommendations: [] }))

    await api.recommend('강남역 근처 2시간')

    const [call] = fetchFn.calls
    expect(call.url).toBe('/api/search/recommendations')
    expect(call.init.method).toBe('POST')
    expect(call.init.headers['X-Session-Id']).toBe(SESSION_UUID)
    expect(call.init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(call.init.body)).toEqual({ text: '강남역 근처 2시간' })
  })

  it('pay는 요청 필드를 그대로 /api/payments로 보낸다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on('POST /api/payments', reply(202, {}))
    const request = {
      passId: 'p1',
      durationHours: 2,
      idempotencyKey: 'k1',
      mockScenario: 'APPROVE',
      mockDelaySeconds: 0,
    }

    await api.pay(request)

    const [call] = fetchFn.calls
    expect(call.url).toBe('/api/payments')
    expect(call.init.headers['X-Session-Id']).toBe(SESSION_UUID)
    expect(JSON.parse(call.init.body)).toEqual(request)
  })

  it('currentReservation과 reservation은 GET이고 헤더로 세션을 보낸다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on('GET /api/reservations/current', reply(200, makeReservation()))
    fetchFn.on('GET /api/reservations/r1', reply(200, makeReservation()))

    await api.currentReservation()
    await api.reservation('r1')

    expect(fetchFn.calls.map((call) => call.url)).toEqual([
      '/api/reservations/current',
      '/api/reservations/r1',
    ])
    for (const call of fetchFn.calls) {
      expect(call.init.method).toBe('GET')
      expect(call.init.headers['X-Session-Id']).toBe(SESSION_UUID)
      expect(call.init.body).toBeUndefined()
    }
  })

  it('경로에 들어가는 예약 ID는 인코딩한다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on('GET /api/reservations/a%2Fb', reply(404, { code: 'NOT_FOUND', message: 'x' }))
    await api.reservation('a/b')
    expect(fetchFn.calls[0].url).toBe('/api/reservations/a%2Fb')
  })

  it('fetch 요청 URL에는 sessionId 쿼리가 없다 (쿼리는 구독 URL에서만)', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on('POST /api/search/recommendations', reply(200, {}))
    fetchFn.on('GET /api/reservations/current', reply(200, {}))
    await api.recommend('x')
    await api.currentReservation()
    for (const call of fetchFn.calls) {
      expect(call.url).not.toContain('sessionId')
      expect(call.url).not.toContain(SESSION_UUID)
    }
  })

  it('세션 ID는 요청마다 새로 읽는다', async () => {
    let current = 'first-id'
    const fetchFn = createFakeFetch().on('GET /api/reservations/current', reply(404, {}))
    const api = createApi({ getSessionId: () => current, fetchFn })
    await api.currentReservation()
    current = 'second-id'
    await api.currentReservation()
    expect(fetchFn.calls.map((call) => call.init.headers['X-Session-Id'])).toEqual([
      'first-id',
      'second-id',
    ])
  })
})

describe('응답 해석', () => {
  it('2xx 본문을 해석한다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on('GET /api/reservations/current', reply(200, makeReservation()))
    const result = await api.currentReservation()
    expect(result).toMatchObject({ outcome: 'response', status: 200 })
    expect(result.body.reservationId).toBe('r1')
    expect(result.error).toBeNull()
  })

  it('오류 본문 { code, message }를 error로 돌려준다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on(
      'POST /api/search/recommendations',
      reply(422, { code: 'MISSING_PLACE', message: '장소를 알려 주세요' }),
    )
    const result = await api.recommend('2시간')
    expect(result.status).toBe(422)
    expect(result.error).toEqual({ code: 'MISSING_PLACE', message: '장소를 알려 주세요' })
  })

  it('409 본문의 reservationId를 보존한다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on(
      'POST /api/payments',
      reply(409, { code: 'PENDING_EXISTS', message: 'm', reservationId: 'r9' }),
    )
    const result = await api.pay({})
    expect(result.error.reservationId).toBe('r9')
  })

  it('JSON이 아닌 본문은 body와 error를 null로 둔다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on('POST /api/payments', reply(502, undefined))
    const result = await api.pay({})
    expect(result).toMatchObject({ outcome: 'response', status: 502, body: null, error: null })
  })

  it('형식이 틀린 오류 본문은 error를 null로 둔다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on('POST /api/payments', reply(500, { oops: true }))
    const result = await api.pay({})
    expect(result.error).toBeNull()
  })

  it('네트워크 오류는 outcome network다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on('POST /api/payments', networkFailure())
    expect(await api.pay({})).toEqual({ outcome: 'network' })
  })
})

describe('3초 제한 시간', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  it('제한 시간은 3000ms다', () => {
    expect(REQUEST_TIMEOUT_MS).toBe(3000)
  })

  it('3초가 지나면 요청을 중단하고 outcome timeout을 돌려준다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on('POST /api/search/recommendations', hang())

    let result = null
    const pending = api.recommend('x').then((value) => {
      result = value
    })
    await vi.advanceTimersByTimeAsync(2999)
    expect(result).toBeNull()
    await vi.advanceTimersByTimeAsync(1)
    await pending
    expect(result).toEqual({ outcome: 'timeout' })
    expect(fetchFn.calls[0].init.signal.aborted).toBe(true)
  })

  it('응답이 오면 타이머를 남기지 않는다', async () => {
    const { fetchFn, api } = setup()
    fetchFn.on('GET /api/reservations/current', reply(200, makeReservation()))
    await api.currentReservation()
    await flushMicrotasks()
    expect(vi.getTimerCount()).toBe(0)
    expect(fetchFn.calls[0].init.signal.aborted).toBe(false)
  })

  it('주입한 제한 시간을 쓴다', async () => {
    const { fetchFn, api } = setup({ timeoutMs: 50 })
    fetchFn.on('GET /api/reservations/r1', hang())
    const pending = api.reservation('r1')
    await vi.advanceTimersByTimeAsync(50)
    expect(await pending).toEqual({ outcome: 'timeout' })
  })
})

describe('구독 URL', () => {
  it('sessionId는 쿼리로, 예약 ID는 인코딩해 만든다', () => {
    expect(buildEventsUrl('r1', SESSION_UUID)).toBe(
      `/api/reservations/r1/events?sessionId=${SESSION_UUID}`,
    )
    expect(buildEventsUrl('a/b', 'x y')).toBe('/api/reservations/a%2Fb/events?sessionId=x%20y')
  })
})

describe('세션 ID 비노출', () => {
  it('실패 경로에서도 콘솔에 세션 ID를 남기지 않는다', async () => {
    const spies = ['log', 'info', 'warn', 'error', 'debug'].map((name) =>
      vi.spyOn(console, name).mockImplementation(() => {}),
    )
    const { fetchFn, api } = setup()
    fetchFn.on('POST /api/payments', networkFailure())
    fetchFn.on('GET /api/reservations/current', reply(500, undefined))
    await api.pay({})
    await api.currentReservation()
    for (const spy of spies) {
      for (const args of spy.mock.calls) {
        expect(JSON.stringify(args)).not.toContain(SESSION_UUID)
      }
    }
  })
})

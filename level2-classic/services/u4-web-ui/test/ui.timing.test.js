// L1 시간 시험 (Vitest + jsdom, 가짜 EventSource·fetch).
// 앱 코드가 이벤트를 받은 즉시 상태를 바꾸고 DOM에 반영하는지를 지키는 회귀 가드다.
// 실제 브라우저의 렌더링·페인트 시간은 증명하지 않는다 (L3 수동 확인 절차의 몫).
// 판정: 예열 20회를 버리고 100회를 잰다. p99가 한도 이하이고 최댓값이 한도의 배수 이하다. 재실행으로 가리지 않는다.
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import App from '../src/App.vue'
import { createApi } from '../src/api/api.js'
import { createSession } from '../src/composables/useSession.js'
import {
  FakeEventSource,
  createFakeFetch,
  createMemoryStorage,
  makeAccepted,
  makeQueryResponse,
  makeRecommendation,
  makeReservation,
  networkFailure,
  reply,
  threeRecommendations,
} from './support/fakes.js'
import { MEASURED_RUNS, WARMUP_RUNS, dropWarmup, judge } from './support/stats.js'

const SEARCH = 'POST /api/search/recommendations'
const PAY = 'POST /api/payments'
const CURRENT = 'GET /api/reservations/current'
const TOTAL_RUNS = WARMUP_RUNS + MEASURED_RUNS

let wrapper
let fetchFn
const clock = { ms: Date.parse('2026-11-08T01:00:00.000Z') }

const microtasks = async (rounds = 40) => {
  for (let i = 0; i < rounds; i += 1) {
    await Promise.resolve()
  }
}

async function mountApp() {
  FakeEventSource.reset()
  fetchFn = createFakeFetch()
  fetchFn.on(CURRENT, reply(404, { code: 'NO_RESERVATION', message: 'x' }))
  const storage = createMemoryStorage()
  const session = createSession({ getStorage: () => storage })
  const api = createApi({ getSessionId: session.getSessionId, fetchFn })
  wrapper = mount(App, {
    props: {
      deps: { api, session, EventSource: FakeEventSource, now: () => clock.ms },
    },
    attachTo: document.body,
  })
  await microtasks()
}

async function reachPending() {
  fetchFn.once(SEARCH, reply(200, makeQueryResponse(threeRecommendations())))
  await wrapper.get('[data-testid="query-input"]').setValue('강남역 근처 2시간')
  await wrapper.get('form').trigger('submit')
  await microtasks()
  fetchFn.once(
    PAY,
    reply(202, makeAccepted({ paymentRequestedAt: new Date(clock.ms).toISOString() })),
  )
  await wrapper.get('[data-testid="pay-p1"]').trigger('click')
  await microtasks()
  expect(wrapper.find('[data-testid="status-pending"]').exists()).toBe(true)
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
  vi.useRealTimers()
})

describe('시간 시험 - 화면 반영 (NFR1.1, NFR3.1)', () => {
  it('이벤트 수신에서 DOM 반영까지 p99 100ms 이하, 최댓값 200ms 이하', async () => {
    await mountApp()
    await reachPending()
    const source = FakeEventSource.open[0]
    const samples = []

    for (let i = 0; i < TOTAL_RUNS; i += 1) {
      const message = `대기 안내 ${i}`
      const start = performance.now()
      source.snapshot(makeReservation({ version: i + 2, message }))
      await nextTick()
      const elapsed = performance.now() - start
      expect(wrapper.get('[data-testid="status-heading"]').text()).toBe(message)
      samples.push(elapsed)
    }

    const result = judge('이벤트 수신 -> DOM 반영', dropWarmup(samples), 100, 2)
    expect(result.p99).toBeLessThanOrEqual(result.limitMs)
    expect(result.max).toBeLessThanOrEqual(result.maxLimit)
  })

  it('추천 응답에서 카드 표시까지 p99 100ms 이하, 최댓값 200ms 이하', async () => {
    await mountApp()
    const input = wrapper.get('[data-testid="query-input"]')
    const samples = []

    for (let i = 0; i < TOTAL_RUNS; i += 1) {
      const passId = `lot-${i}`
      fetchFn.once(
        SEARCH,
        reply(200, makeQueryResponse([makeRecommendation({ passId, lotName: `주차장 ${i}` })])),
      )
      await input.setValue(`강남역 근처 2시간 ${i}`)
      const start = performance.now()
      wrapper.get('form').trigger('submit')
      for (let n = 0; n < 200 && !wrapper.find(`[data-testid="card-${passId}"]`).exists(); n += 1) {
        await Promise.resolve()
      }
      const elapsed = performance.now() - start
      expect(wrapper.find(`[data-testid="card-${passId}"]`).exists()).toBe(true)
      samples.push(elapsed)
    }

    const result = judge('추천 응답 -> 카드 표시', dropWarmup(samples), 100, 2)
    expect(result.p99).toBeLessThanOrEqual(result.limitMs)
    expect(result.max).toBeLessThanOrEqual(result.maxLimit)
  })
})

describe('시간 시험 - 경과 시간 틱 (NFR1.3)', () => {
  it('틱 한 번의 갱신이 p99 5ms 이하, 최댓값 20ms 이하', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] })
    clock.ms = Date.parse('2026-11-08T01:00:00.000Z')
    await mountApp()
    await reachPending()
    const samples = []

    for (let i = 1; i <= TOTAL_RUNS; i += 1) {
      clock.ms += 1000
      const start = performance.now()
      vi.advanceTimersByTime(1000)
      await nextTick()
      const elapsed = performance.now() - start
      expect(wrapper.get('[data-testid="status-pending"]').text()).toContain(
        `경과 ${i}초 / 최대 180초`,
      )
      samples.push(elapsed)
    }

    const result = judge('경과 시간 틱', dropWarmup(samples), 5, 4)
    expect(result.p99).toBeLessThanOrEqual(result.limitMs)
    expect(result.max).toBeLessThanOrEqual(result.maxLimit)
  })
})

describe('시간 시험 - 복구 간격과 총 시간 (NFR1.4, 가짜 시계)', () => {
  it('1·2·5·5초 물러나기로 시도하고 60초에 새로 고침 안내를 보인 뒤 멈춘다', async () => {
    vi.useFakeTimers({
      toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout', 'Date'],
    })
    clock.ms = Date.now()
    await mountApp()
    await reachPending()

    const attemptTimes = []
    const startedAt = Date.now()
    fetchFn.on('GET /api/reservations/r1', (...args) => {
      attemptTimes.push(Date.now() - startedAt)
      return networkFailure()(...args)
    })
    FakeEventSource.open[0].failClosed()

    await vi.advanceTimersByTimeAsync(59999)
    expect(wrapper.find('[data-testid="stream-notice"]').exists()).toBe(false)
    await vi.advanceTimersByTimeAsync(1)
    await nextTick()

    expect(attemptTimes).toEqual([
      1000, 3000, 8000, 13000, 18000, 23000, 28000, 33000, 38000, 43000, 48000, 53000, 58000,
    ])
    expect(wrapper.get('[data-testid="stream-notice"]').text()).toBe(
      '(!) 실시간 연결이 불안정합니다. 새로 고침해 주세요',
    )
    expect(wrapper.get('[data-testid="live-region"]').text()).toBe(
      '실시간 연결이 불안정합니다. 새로 고침해 주세요',
    )
    // 마지막으로 받은 상태는 유지하고 더 시도하지 않는다
    expect(wrapper.find('[data-testid="status-pending"]').exists()).toBe(true)
    await vi.advanceTimersByTimeAsync(120000)
    expect(attemptTimes).toHaveLength(13)
  })
})

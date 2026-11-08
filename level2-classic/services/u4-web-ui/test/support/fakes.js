// 시험 도구: 가짜 fetch, 가짜 EventSource, 가짜 저장소, 시험 데이터.
import { vi } from 'vitest'

export const SESSION_UUID = '3f2b8c1e-5a47-4d9e-9a3b-0c1d2e3f4a5b'

// ---------- 데이터 ----------

export function makeRecommendation(overrides = {}) {
  return {
    passId: 'p1',
    lotName: '강남역 공영주차장',
    price: 6000,
    distanceMeters: 120,
    score: 92,
    rank: 'BEST',
    reason: { topFactor: 'PRICE', text: '가격이 가장 큰 강점' },
    ...overrides,
  }
}

export function makeQueryResponse(recommendations, durationHours = 2) {
  return { query: { place: '강남역', durationHours }, recommendations }
}

export function threeRecommendations() {
  return [
    makeRecommendation(),
    makeRecommendation({
      passId: 'p2',
      lotName: '역삼 빌딩 주차장',
      price: 7000,
      distanceMeters: 80,
      score: 72,
      rank: 'CANDIDATE',
      reason: { topFactor: 'DISTANCE', text: '거리가 가까움' },
    }),
    makeRecommendation({
      passId: 'p3',
      lotName: '테헤란로 주차장',
      price: 5000,
      distanceMeters: 300,
      score: 65,
      rank: 'CANDIDATE',
      reason: { topFactor: 'PRICE', text: '가격이 낮음' },
    }),
  ]
}

export function makeReservation(overrides = {}) {
  return {
    reservationId: 'r1',
    version: 1,
    status: 'PENDING_PAYMENT',
    lotName: '강남역 공영주차장',
    durationHours: 2,
    amount: 6000,
    paymentRequestedAt: '2026-11-08T01:00:00.000Z',
    message: '결제 승인을 기다리는 중입니다...',
    ...overrides,
  }
}

export function makeAccepted(overrides = {}) {
  return {
    paymentId: 'pay1',
    reservationId: 'r1',
    status: 'PENDING_PAYMENT',
    paymentRequestedAt: '2026-11-08T01:00:00.000Z',
    ...overrides,
  }
}

// ---------- 저장소 ----------

export function createMemoryStorage() {
  const data = new Map()
  return {
    getItem: (key) => (data.has(key) ? data.get(key) : null),
    setItem: (key, value) => {
      data.set(key, String(value))
    },
    removeItem: (key) => {
      data.delete(key)
    },
    snapshot: () => Object.fromEntries(data),
  }
}

/** 모든 접근이 던지는 저장소 (차단된 sessionStorage). */
export function createBrokenStorage() {
  const fail = () => {
    throw new Error('storage blocked')
  }
  return { getItem: fail, setItem: fail, removeItem: fail }
}

// ---------- fetch ----------

export function fakeResponse(status, body) {
  return {
    status,
    ok: status >= 200 && status < 300,
    json: async () => {
      if (body === undefined) {
        throw new SyntaxError('Unexpected end of JSON input')
      }
      return body
    },
  }
}

export const reply = (status, body) => () => Promise.resolve(fakeResponse(status, body))
export const networkFailure = () => () => Promise.reject(new TypeError('Failed to fetch'))
/** 응답이 오지 않다가 중단 신호를 받으면 AbortError로 끝난다. */
export const hang = () => (_url, init) =>
  new Promise((_resolve, reject) => {
    init.signal.addEventListener('abort', () => {
      reject(new DOMException('The operation was aborted.', 'AbortError'))
    })
  })

/** 시험이 직접 풀어 주는 응답. */
export function deferred() {
  let resolveFn
  const promise = new Promise((resolve) => {
    resolveFn = resolve
  })
  return {
    handler: () => promise,
    resolve: (status, body) => resolveFn(fakeResponse(status, body)),
  }
}

/**
 * 경로별로 응답을 정하는 가짜 fetch.
 * once('POST /api/payments', reply(...))는 한 번만, on(...)은 계속 쓰인다.
 */
export function createFakeFetch() {
  const calls = []
  const queues = new Map()
  const defaults = new Map()

  const fetchFn = vi.fn((url, init = {}) => {
    const method = (init.method ?? 'GET').toUpperCase()
    const path = String(url).split('?')[0]
    const key = `${method} ${path}`
    calls.push({ key, url: String(url), init })
    const queue = queues.get(key)
    const handler = queue?.length ? queue.shift() : defaults.get(key)
    if (!handler) {
      throw new Error(`가짜 fetch에 준비된 응답이 없습니다: ${key}`)
    }
    return handler(url, init)
  })

  fetchFn.once = (key, handler) => {
    if (!queues.has(key)) {
      queues.set(key, [])
    }
    queues.get(key).push(handler)
    return fetchFn
  }
  fetchFn.on = (key, handler) => {
    defaults.set(key, handler)
    return fetchFn
  }
  fetchFn.calls = calls
  fetchFn.callsFor = (key) => calls.filter((call) => call.key === key)
  fetchFn.bodyOf = (call) => JSON.parse(call.init.body)
  return fetchFn
}

// ---------- EventSource ----------

export class FakeEventSource {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSED = 2
  static instances = []

  static reset() {
    FakeEventSource.instances = []
  }

  static get open() {
    return FakeEventSource.instances.filter((es) => es.readyState !== 2)
  }

  constructor(url) {
    this.url = url
    this.readyState = 0
    this.closeCount = 0
    this.onopen = null
    this.onerror = null
    this.listeners = new Map()
    FakeEventSource.instances.push(this)
  }

  addEventListener(type, listener) {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, [])
    }
    this.listeners.get(type).push(listener)
  }

  removeEventListener(type, listener) {
    const list = this.listeners.get(type) ?? []
    this.listeners.set(
      type,
      list.filter((item) => item !== listener),
    )
  }

  close() {
    this.readyState = 2
    this.closeCount += 1
  }

  // --- 시험이 쓰는 도구 ---
  markOpen() {
    this.readyState = 1
    this.onopen?.({ type: 'open' })
  }

  emit(type, data) {
    if (this.readyState === 2) {
      return
    }
    const event = { type, data: typeof data === 'string' ? data : JSON.stringify(data) }
    for (const listener of this.listeners.get(type) ?? []) {
      listener(event)
    }
  }

  snapshot(reservation) {
    this.emit('snapshot', reservation)
  }

  /** 브라우저가 자동 재연결 중인 오류. */
  failReconnecting() {
    this.readyState = 0
    this.onerror?.({ type: 'error' })
  }

  /** 브라우저가 포기한 오류 (404, 503, 401 등). */
  failClosed() {
    this.readyState = 2
    this.onerror?.({ type: 'error' })
  }
}

// ---------- 기타 ----------

export async function flushPromises(rounds = 5) {
  for (let i = 0; i < rounds; i += 1) {
    await Promise.resolve()
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

/** 가짜 타이머 아래에서 마이크로태스크만 비운다. */
export async function flushMicrotasks(rounds = 10) {
  for (let i = 0; i < rounds; i += 1) {
    await Promise.resolve()
  }
}

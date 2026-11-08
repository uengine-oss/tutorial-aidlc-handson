import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useReservationStream } from './useReservationStream.js'
import { FakeEventSource, SESSION_UUID, makeReservation } from '../../test/support/fakes.js'

const found = (reservation) => ({
  outcome: 'response',
  status: 200,
  body: reservation,
  error: null,
})
const missing = (status) => ({ outcome: 'response', status, body: null, error: null })
const networkDown = { outcome: 'network' }

function setup() {
  FakeEventSource.reset()
  const api = { reservation: vi.fn() }
  const handlers = {
    onReservation: vi.fn(),
    onGone: vi.fn(),
    onSessionLost: vi.fn(),
    onUnstable: vi.fn(),
  }
  const warn = vi.fn()
  const stream = useReservationStream({
    api,
    getSessionId: () => SESSION_UUID,
    EventSource: FakeEventSource,
    now: () => Date.now(),
    warn,
    ...handlers,
  })
  return { api, handlers, stream, warn }
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('구독 열기와 닫기', () => {
  it('예약 한 건의 이벤트 URL로 구독한다', () => {
    const { stream } = setup()
    stream.follow('r1')
    expect(FakeEventSource.instances).toHaveLength(1)
    expect(FakeEventSource.instances[0].url).toBe(
      `/api/reservations/r1/events?sessionId=${SESSION_UUID}`,
    )
  })

  it('새 구독을 만들기 전에 기존 구독을 닫아 하나만 남긴다', () => {
    const { stream } = setup()
    stream.follow('r1')
    stream.follow('r2')
    expect(FakeEventSource.instances).toHaveLength(2)
    expect(FakeEventSource.instances[0].closeCount).toBe(1)
    expect(FakeEventSource.open).toHaveLength(1)
    expect(FakeEventSource.open[0].url).toContain('/r2/events')
  })

  it('close는 구독을 닫고 이후 이벤트를 무시한다', () => {
    const { stream, handlers } = setup()
    stream.follow('r1')
    const [source] = FakeEventSource.instances
    stream.close()
    expect(source.closeCount).toBe(1)
    source.emit('snapshot', makeReservation())
    expect(handlers.onReservation).not.toHaveBeenCalled()
    expect(stream.currentId()).toBeNull()
  })
})

describe('스냅샷 반영과 version 필터', () => {
  it('스냅샷을 받으면 바로 전달한다', () => {
    const { stream, handlers } = setup()
    stream.follow('r1')
    const reservation = makeReservation({ version: 1 })
    FakeEventSource.instances[0].snapshot(reservation)
    expect(handlers.onReservation).toHaveBeenCalledWith(reservation)
  })

  it('같은 예약에서 version이 낮거나 같은 스냅샷은 버린다', () => {
    const { stream, handlers } = setup()
    stream.follow('r1')
    const [source] = FakeEventSource.instances
    source.snapshot(makeReservation({ version: 2, message: 'v2' }))
    source.snapshot(makeReservation({ version: 1, message: 'v1' }))
    source.snapshot(makeReservation({ version: 2, message: 'again' }))
    source.snapshot(makeReservation({ version: 3, message: 'v3' }))
    expect(handlers.onReservation.mock.calls.map(([r]) => r.message)).toEqual(['v2', 'v3'])
  })

  it('새 예약은 version 1부터 반영된다 (예약별 필터)', () => {
    const { stream, handlers } = setup()
    stream.follow('r1')
    FakeEventSource.instances[0].snapshot(makeReservation({ reservationId: 'r1', version: 4 }))
    stream.follow('r2')
    FakeEventSource.instances[1].snapshot(
      makeReservation({ reservationId: 'r2', version: 1, message: 'new' }),
    )
    expect(handlers.onReservation).toHaveBeenLastCalledWith(
      expect.objectContaining({ reservationId: 'r2', message: 'new' }),
    )
  })

  it('accept는 lookup 응답에도 같은 필터를 적용한다', () => {
    const { stream } = setup()
    expect(stream.accept(makeReservation({ version: 2 }))).toBe(true)
    expect(stream.accept(makeReservation({ version: 2 }))).toBe(false)
    expect(stream.accept(makeReservation({ version: 1 }))).toBe(false)
    expect(stream.accept(makeReservation({ version: 3 }))).toBe(true)
    stream.resetVersion('r1')
    expect(stream.accept(makeReservation({ version: 1 }))).toBe(true)
  })

  it('follow의 reset을 끄면 이미 본 version을 유지한다', () => {
    const { stream } = setup()
    stream.accept(makeReservation({ version: 3 }))
    stream.follow('r1', { reset: false })
    expect(stream.accept(makeReservation({ version: 3 }))).toBe(false)
  })

  it.each([
    ['깨진 JSON', '{not json'],
    ['다른 reservationId', JSON.stringify(makeReservation({ reservationId: 'other' }))],
    ['정수 아닌 version', JSON.stringify(makeReservation({ version: 1.5 }))],
    ['알 수 없는 status', JSON.stringify(makeReservation({ status: 'REFUNDED' }))],
    ['필드 누락', JSON.stringify({ reservationId: 'r1', version: 1 })],
  ])('무시한다 (처리기가 멈추지 않는다): %s', (_label, payload) => {
    const { stream, handlers, warn } = setup()
    stream.follow('r1')
    const [source] = FakeEventSource.instances
    source.emit('snapshot', payload)
    expect(handlers.onReservation).not.toHaveBeenCalled()
    expect(warn).toHaveBeenCalledTimes(1)
    expect(JSON.stringify(warn.mock.calls)).not.toContain(SESSION_UUID)
    // 다음 정상 스냅샷은 그대로 반영된다
    source.snapshot(makeReservation({ version: 1 }))
    expect(handlers.onReservation).toHaveBeenCalledTimes(1)
  })

  it.each(['CONFIRMED', 'PAYMENT_FAILED', 'CANCELLED'])(
    '종료 상태 %s를 받으면 반영한 뒤 구독을 닫는다',
    (status) => {
      const { stream, handlers } = setup()
      stream.follow('r1')
      const [source] = FakeEventSource.instances
      source.snapshot(makeReservation({ version: 2, status }))
      expect(handlers.onReservation).toHaveBeenCalledTimes(1)
      expect(source.closeCount).toBe(1)
      expect(stream.currentId()).toBeNull()
      source.snapshot(makeReservation({ version: 3, status }))
      expect(handlers.onReservation).toHaveBeenCalledTimes(1)
    },
  )

  it('PENDING_PAYMENT 스냅샷으로는 구독을 닫지 않는다', () => {
    const { stream } = setup()
    stream.follow('r1')
    FakeEventSource.instances[0].snapshot(makeReservation({ version: 1 }))
    expect(FakeEventSource.instances[0].closeCount).toBe(0)
  })
})

describe('닫힌 연결 복구', () => {
  it('브라우저가 재연결 중인 오류는 복구를 시작하지 않는다', async () => {
    const { stream, api } = setup()
    stream.follow('r1')
    FakeEventSource.instances[0].failReconnecting()
    await vi.advanceTimersByTimeAsync(60000)
    expect(api.reservation).not.toHaveBeenCalled()
  })

  it('CLOSED가 되면 1초 뒤 단건 조회로 반영하고 다시 구독한다', async () => {
    const { stream, api, handlers } = setup()
    api.reservation.mockResolvedValue(found(makeReservation({ version: 2, message: 'm' })))
    stream.follow('r1')
    FakeEventSource.instances[0].failClosed()

    await vi.advanceTimersByTimeAsync(999)
    expect(api.reservation).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(api.reservation).toHaveBeenCalledWith('r1')
    expect(handlers.onReservation).toHaveBeenCalledWith(expect.objectContaining({ version: 2 }))
    expect(FakeEventSource.instances).toHaveLength(2)
    expect(FakeEventSource.open).toHaveLength(1)
  })

  it('조회가 실패하면 1초, 2초, 5초, 5초... 로 물러나며 다시 시도한다', async () => {
    const { stream, api } = setup()
    api.reservation.mockResolvedValue(networkDown)
    stream.follow('r1')
    FakeEventSource.instances[0].failClosed()

    const attemptTimes = []
    const start = Date.now()
    api.reservation.mockImplementation(async () => {
      attemptTimes.push(Date.now() - start)
      return networkDown
    })
    await vi.advanceTimersByTimeAsync(20000)
    expect(attemptTimes).toEqual([1000, 3000, 8000, 13000, 18000])
  })

  it('총 60초 안에서만 시도하고 넘으면 안내 후 멈춘다', async () => {
    const { stream, api, handlers } = setup()
    const attemptTimes = []
    const start = Date.now()
    api.reservation.mockImplementation(async () => {
      attemptTimes.push(Date.now() - start)
      return networkDown
    })
    stream.follow('r1')
    FakeEventSource.instances[0].failClosed()

    await vi.advanceTimersByTimeAsync(59999)
    expect(handlers.onUnstable).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(handlers.onUnstable).toHaveBeenCalledTimes(1)
    expect(attemptTimes).toEqual([
      1000, 3000, 8000, 13000, 18000, 23000, 28000, 33000, 38000, 43000, 48000, 53000, 58000,
    ])
    expect(vi.getTimerCount()).toBe(0)
    await vi.advanceTimersByTimeAsync(120000)
    expect(api.reservation).toHaveBeenCalledTimes(13)
    expect(handlers.onUnstable).toHaveBeenCalledTimes(1)
  })

  it('조회가 404이면 예약이 사라진 것으로 보고 알린 뒤 멈춘다', async () => {
    const { stream, api, handlers } = setup()
    api.reservation.mockResolvedValue(missing(404))
    stream.follow('r1')
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    expect(handlers.onGone).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(0)
    expect(FakeEventSource.open).toHaveLength(0)
    await vi.advanceTimersByTimeAsync(60000)
    expect(api.reservation).toHaveBeenCalledTimes(1)
  })

  it('조회가 401이면 세션 안내를 알리고 멈춘다', async () => {
    const { stream, api, handlers } = setup()
    api.reservation.mockResolvedValue(missing(401))
    stream.follow('r1')
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    expect(handlers.onSessionLost).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(0)
    await vi.advanceTimersByTimeAsync(60000)
    expect(api.reservation).toHaveBeenCalledTimes(1)
  })

  it('조회 결과가 종료 상태이면 반영하고 다시 구독하지 않는다', async () => {
    const { stream, api, handlers } = setup()
    api.reservation.mockResolvedValue(found(makeReservation({ version: 3, status: 'CONFIRMED' })))
    stream.follow('r1')
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    expect(handlers.onReservation).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'CONFIRMED' }),
    )
    expect(FakeEventSource.instances).toHaveLength(1)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('낡은 version의 조회 결과는 반영하지 않지만 다시 구독은 한다', async () => {
    const { stream, api, handlers } = setup()
    stream.follow('r1')
    FakeEventSource.instances[0].snapshot(makeReservation({ version: 5 }))
    handlers.onReservation.mockClear()
    api.reservation.mockResolvedValue(found(makeReservation({ version: 4 })))
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    expect(handlers.onReservation).not.toHaveBeenCalled()
    expect(FakeEventSource.instances).toHaveLength(2)
  })

  it('다시 구독한 연결이 열리기 전에 또 닫히면 물러나기를 이어 간다', async () => {
    const { stream, api } = setup()
    api.reservation.mockResolvedValue(found(makeReservation({ version: 2 })))
    stream.follow('r1')
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    expect(api.reservation).toHaveBeenCalledTimes(1)

    FakeEventSource.instances[1].failClosed()
    await vi.advanceTimersByTimeAsync(1999)
    expect(api.reservation).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(api.reservation).toHaveBeenCalledTimes(2)
  })

  it('다시 구독한 연결이 열리면 복구가 끝나 다음 오류는 1초부터 다시 시작한다', async () => {
    const { stream, api } = setup()
    api.reservation.mockResolvedValue(found(makeReservation({ version: 2 })))
    stream.follow('r1')
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    FakeEventSource.instances[1].markOpen()

    FakeEventSource.instances[1].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    expect(api.reservation).toHaveBeenCalledTimes(2)
  })

  it('복구 중에 close하면 타이머를 모두 거둔다', async () => {
    const { stream, api } = setup()
    api.reservation.mockResolvedValue(networkDown)
    stream.follow('r1')
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    stream.close()
    expect(vi.getTimerCount()).toBe(0)
    await vi.advanceTimersByTimeAsync(60000)
    expect(api.reservation).toHaveBeenCalledTimes(1)
  })

  it('복구 중에 다른 예약을 구독하면 이전 복구 결과는 무시한다', async () => {
    const { stream, api, handlers } = setup()
    let release
    api.reservation.mockImplementation(
      () =>
        new Promise((resolve) => {
          release = () => resolve(found(makeReservation({ reservationId: 'r1', version: 9 })))
        }),
    )
    stream.follow('r1')
    FakeEventSource.instances[0].failClosed()
    await vi.advanceTimersByTimeAsync(1000)
    stream.follow('r2')
    release()
    await vi.advanceTimersByTimeAsync(0)
    expect(handlers.onReservation).not.toHaveBeenCalled()
    expect(FakeEventSource.open).toHaveLength(1)
    expect(FakeEventSource.open[0].url).toContain('/r2/events')
  })

  it('복구가 시작된 구독에서 reset하면 필터까지 비운다', async () => {
    const { stream } = setup()
    stream.follow('r1')
    stream.accept(makeReservation({ version: 7 }))
    stream.reset()
    expect(stream.accept(makeReservation({ version: 1 }))).toBe(true)
    expect(FakeEventSource.open).toHaveLength(0)
  })
})

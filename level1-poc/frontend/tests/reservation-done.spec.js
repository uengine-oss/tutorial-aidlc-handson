import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ReservationDone from '../src/components/ReservationDone.vue'
import { watchReservation } from '../src/events.js'

/** SSE를 대신하는 가짜 EventSource */
class FakeEventSource {
  static last = null
  constructor(url) {
    this.url = url
    this.listeners = {}
    this.closed = false
    FakeEventSource.last = this
  }
  addEventListener(name, fn) {
    this.listeners[name] = fn
  }
  emit(name, data) {
    this.listeners[name]?.({ data: JSON.stringify(data) })
  }
  close() {
    this.closed = true
  }
}

afterEach(() => {
  vi.useRealTimers()
})

describe('예약 완료 화면', () => {
  it('예약 정보를 보여 준다', () => {
    const wrapper = mount(ReservationDone, {
      props: { reservation: { id: 'r1', ticketName: '강남역 공영', price: 6000, status: 'CONFIRMED' } }
    })
    expect(wrapper.get('[data-testid="reservation-done"]').text()).toContain('예약이 완료')
    expect(wrapper.get('[data-testid="done-ticket"]').text()).toContain('6,000원')
  })
})

describe('watchReservation (SSE)', () => {
  it('CONFIRMED 이벤트를 받으면 콜백을 부르고 연결을 닫는다', () => {
    const seen = []
    watchReservation('r1', (r) => seen.push(r.status), { EventSourceImpl: FakeEventSource })
    const es = FakeEventSource.last
    expect(es.url).toBe('/api/reservations/r1/events')

    es.emit('status', { id: 'r1', status: 'PENDING' })
    es.emit('status', { id: 'r1', status: 'CONFIRMED' })

    expect(seen).toEqual(['PENDING', 'CONFIRMED'])
    expect(es.closed).toBe(true)
  })

  it('깨진 이벤트는 무시한다', () => {
    const seen = []
    watchReservation('r1', (r) => seen.push(r.status), { EventSourceImpl: FakeEventSource })
    FakeEventSource.last.listeners.status({ data: '{not json' })
    expect(seen).toEqual([])
  })

  it('SSE 오류가 나면 폴링으로 바꿔 확정 상태를 받는다', async () => {
    vi.useFakeTimers()
    const seen = []
    const fetchStatus = vi.fn().mockResolvedValue({ id: 'r1', status: 'CONFIRMED' })
    watchReservation('r1', (r) => seen.push(r.status), {
      EventSourceImpl: FakeEventSource,
      fetchStatus,
      pollMs: 500
    })
    FakeEventSource.last.onerror()
    await vi.advanceTimersByTimeAsync(600)

    expect(fetchStatus).toHaveBeenCalledWith('r1')
    expect(seen).toEqual(['CONFIRMED'])
  })

  it('stop을 부르면 이후 이벤트를 전달하지 않는다', () => {
    const seen = []
    const stop = watchReservation('r1', (r) => seen.push(r.status), { EventSourceImpl: FakeEventSource })
    stop()
    FakeEventSource.last.emit('status', { id: 'r1', status: 'CONFIRMED' })
    expect(seen).toEqual([])
  })
})

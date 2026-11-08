// 예약 상태 변화를 SSE로 받는다. SSE가 끊기면 폴링으로 전환한다 (FR4.2, NFR1).

const FINAL = ['CONFIRMED', 'CANCELLED']

/**
 * @param {string} reservationId
 * @param {(reservation: object) => void} onStatus 상태가 도착할 때마다 호출
 * @param {object} options
 * @param {Function} [options.EventSourceImpl] 테스트에서 가짜로 바꾸는 EventSource
 * @param {(id: string) => Promise<object>} options.fetchStatus 폴링용 조회 함수
 * @param {number} [options.pollMs]
 * @returns {() => void} 구독을 멈추는 함수
 */
export function watchReservation(reservationId, onStatus, options = {}) {
  const { EventSourceImpl = globalThis.EventSource, fetchStatus, pollMs = 1000 } = options
  let stopped = false
  let source = null
  let timer = null

  function handle(reservation) {
    if (stopped) return
    onStatus(reservation)
    if (FINAL.includes(reservation.status)) stop()
  }

  function startPolling() {
    if (timer || stopped || !fetchStatus) return
    timer = setInterval(async () => {
      try {
        handle(await fetchStatus(reservationId))
      } catch (e) {
        // 일시적인 오류는 다음 주기에 다시 시도한다.
      }
    }, pollMs)
  }

  function stop() {
    stopped = true
    if (source) source.close()
    if (timer) clearInterval(timer)
  }

  if (EventSourceImpl) {
    source = new EventSourceImpl(`/api/reservations/${reservationId}/events`)
    source.addEventListener('status', (e) => {
      try {
        handle(JSON.parse(e.data))
      } catch (err) {
        // 깨진 이벤트는 무시하고 다음 이벤트를 기다린다.
      }
    })
    source.onerror = () => {
      if (source) source.close()
      startPolling()
    }
  } else {
    startPolling()
  }

  return stop
}

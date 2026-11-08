import { isApiError } from '../lib/guards.js'

export const REQUEST_TIMEOUT_MS = 3000

/**
 * 구독(EventSource) URL. 브라우저 EventSource는 헤더를 보낼 수 없어 세션을 쿼리로 보낸다.
 * 세션 ID가 URL에 실리는 곳은 이 함수 하나뿐이다 (NFR6.4).
 */
export function buildEventsUrl(reservationId, sessionId) {
  return `/api/reservations/${encodeURIComponent(reservationId)}/events?sessionId=${encodeURIComponent(sessionId)}`
}

/**
 * 상대 경로 fetch 래퍼. 결과는 던지지 않고 값으로 돌려준다.
 *  - { outcome: 'response', status, body, error }  body는 해석한 JSON(실패 시 null), error는 오류 본문(없으면 null)
 *  - { outcome: 'timeout' }                         제한 시간 안에 응답이 없음
 *  - { outcome: 'network' }                         연결 실패
 * 세션 ID는 로그나 오류 문구에 넣지 않는다.
 * @param {{ getSessionId: () => string, fetchFn?: typeof fetch, timeoutMs?: number }} deps
 */
export function createApi({
  getSessionId,
  fetchFn = (...args) => globalThis.fetch(...args),
  timeoutMs = REQUEST_TIMEOUT_MS,
}) {
  async function request(method, path, payload) {
    const controller = new AbortController()
    let timedOut = false
    const timer = setTimeout(() => {
      timedOut = true
      controller.abort()
    }, timeoutMs)

    const headers = { 'X-Session-Id': getSessionId() }
    const init = { method, headers, signal: controller.signal }
    if (payload !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(payload)
    }

    try {
      const response = await fetchFn(path, init)
      let body = null
      try {
        body = await response.json()
      } catch {
        body = null
      }
      return {
        outcome: 'response',
        status: response.status,
        body,
        error: isApiError(body) ? body : null,
      }
    } catch {
      return { outcome: timedOut ? 'timeout' : 'network' }
    } finally {
      clearTimeout(timer)
    }
  }

  return {
    recommend: (text) => request('POST', '/api/search/recommendations', { text }),
    pay: (payment) => request('POST', '/api/payments', payment),
    currentReservation: () => request('GET', '/api/reservations/current'),
    reservation: (id) => request('GET', `/api/reservations/${encodeURIComponent(id)}`),
  }
}

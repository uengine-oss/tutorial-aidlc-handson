// 백엔드 호출 모음. 실패하면 사람이 읽을 수 있는 메시지를 담은 ApiError를 던진다.

export class ApiError extends Error {
  // code: 결제 서비스 표준 오류 객체 { code, message }의 code (다른 서비스 오류에는 없을 수 있다)
  constructor(message, status, code = null) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function request(url, options = {}) {
  let res
  try {
    res = await fetch(url, {
      headers: { 'Content-Type': 'application/json' },
      ...options
    })
  } catch (e) {
    throw new ApiError('서버에 연결하지 못했어요. 잠시 뒤 다시 시도해 주세요.', 0)
  }
  const body = await res.json().catch(() => null)
  if (!res.ok) {
    throw new ApiError(
      body?.message || `요청에 실패했어요 (${res.status})`,
      res.status,
      typeof body?.code === 'string' ? body.code : null
    )
  }
  return body
}

const post = (url, data) =>
  request(url, { method: 'POST', body: data === undefined ? undefined : JSON.stringify(data) })

export const recommend = (text) => post('/api/recommend', { text })
export const createReservation = (ticket) =>
  post('/api/reservations', {
    ticketId: ticket.id,
    ticketName: ticket.lotName,
    price: ticket.price
  })
export const getReservation = (id) => request(`/api/reservations/${id}`)
export const createPayment = (reservationId, amount) =>
  post('/api/payments', { reservationId, amount })
export const getPayment = (id) => request(`/api/payments/${id}`)
export const mockPgApprove = (paymentId) => post(`/api/mock-pg/${paymentId}/approve`)
export const mockPgFail = (paymentId) => post(`/api/mock-pg/${paymentId}/fail`)

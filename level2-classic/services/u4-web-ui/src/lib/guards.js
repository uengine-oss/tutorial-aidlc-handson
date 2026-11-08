// 서버 응답의 형식 확인 (NFR6.5). 필드의 존재와 형식만 보고, 규칙 판단은 서비스가 한다.

const STATUS_TO_VIEW = {
  PENDING_PAYMENT: 'pending',
  CONFIRMED: 'confirmed',
  PAYMENT_FAILED: 'failed',
  CANCELLED: 'cancelled',
}

const TERMINAL_STATUSES = ['CONFIRMED', 'PAYMENT_FAILED', 'CANCELLED']

const isObject = (value) => typeof value === 'object' && value !== null && !Array.isArray(value)
const isNonEmptyString = (value) => typeof value === 'string' && value.length > 0

/** @param {unknown} status @returns {'pending'|'confirmed'|'failed'|'cancelled'|null} */
export function statusToView(status) {
  return Object.hasOwn(STATUS_TO_VIEW, status) ? STATUS_TO_VIEW[status] : null
}

/** @param {unknown} status */
export function isTerminalStatus(status) {
  return TERMINAL_STATUSES.includes(status)
}

/** 추천 한 건 (C1 Recommendation). @param {unknown} value */
export function isRecommendation(value) {
  return (
    isObject(value) &&
    isNonEmptyString(value.passId) &&
    typeof value.lotName === 'string' &&
    Number.isInteger(value.price) &&
    Number.isInteger(value.distanceMeters) &&
    typeof value.score === 'number' &&
    (value.rank === 'BEST' || value.rank === 'CANDIDATE') &&
    isObject(value.reason) &&
    typeof value.reason.text === 'string'
  )
}

/** 추천 응답 전체 (C1 200). @param {unknown} value */
export function isRecommendationResponse(value) {
  return (
    isObject(value) &&
    isObject(value.query) &&
    Number.isInteger(value.query.durationHours) &&
    value.query.durationHours >= 1 &&
    Array.isArray(value.recommendations) &&
    value.recommendations.every(isRecommendation)
  )
}

/** 예약 스냅샷 (C3 Reservation). @param {unknown} value */
export function isReservation(value) {
  return (
    isObject(value) &&
    isNonEmptyString(value.reservationId) &&
    Number.isInteger(value.version) &&
    statusToView(value.status) !== null &&
    typeof value.lotName === 'string' &&
    Number.isInteger(value.durationHours) &&
    Number.isInteger(value.amount) &&
    typeof value.paymentRequestedAt === 'string' &&
    (value.message === undefined || typeof value.message === 'string')
  )
}

/** 오류 본문 { code, message, reservationId? }. @param {unknown} value */
export function isApiError(value) {
  return (
    isObject(value) &&
    typeof value.code === 'string' &&
    (value.message === undefined || typeof value.message === 'string') &&
    (value.reservationId === undefined || typeof value.reservationId === 'string')
  )
}

/** 결제 접수 응답 (C2 202/200). @param {unknown} value */
export function isPaymentAccepted(value) {
  return (
    isObject(value) &&
    isNonEmptyString(value.reservationId) &&
    statusToView(value.status) !== null &&
    typeof value.paymentRequestedAt === 'string'
  )
}

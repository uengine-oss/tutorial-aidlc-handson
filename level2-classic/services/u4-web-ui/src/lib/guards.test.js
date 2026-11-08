import { describe, expect, it } from 'vitest'
import {
  isApiError,
  isPaymentAccepted,
  isRecommendation,
  isRecommendationResponse,
  isReservation,
  isTerminalStatus,
  statusToView,
} from './guards.js'

const recommendation = (overrides = {}) => ({
  passId: 'p1',
  lotName: '강남역 공영주차장',
  price: 6000,
  distanceMeters: 120,
  score: 92,
  rank: 'BEST',
  reason: { topFactor: 'PRICE', text: '가격이 가장 큰 강점' },
  ...overrides,
})

const reservation = (overrides = {}) => ({
  reservationId: 'r1',
  version: 1,
  status: 'PENDING_PAYMENT',
  lotName: '강남역 공영주차장',
  durationHours: 2,
  amount: 6000,
  paymentRequestedAt: '2026-11-08T01:00:00Z',
  ...overrides,
})

describe('isRecommendation', () => {
  it('정상 추천 한 건을 받아들인다', () => {
    expect(isRecommendation(recommendation())).toBe(true)
    expect(isRecommendation(recommendation({ rank: 'CANDIDATE' }))).toBe(true)
  })

  it.each([
    ['null', null],
    ['문자열', 'x'],
    ['passId 누락', recommendation({ passId: undefined })],
    ['빈 passId', recommendation({ passId: '' })],
    ['정수 아닌 price', recommendation({ price: 6000.5 })],
    ['문자열 price', recommendation({ price: '6000' })],
    ['알 수 없는 rank', recommendation({ rank: 'WORST' })],
    ['reason 누락', recommendation({ reason: undefined })],
    ['reason.text 누락', recommendation({ reason: { topFactor: 'PRICE' } })],
    ['score 누락', recommendation({ score: undefined })],
  ])('거부한다: %s', (_label, value) => {
    expect(isRecommendation(value)).toBe(false)
  })
})

describe('isRecommendationResponse', () => {
  const body = (overrides = {}) => ({
    query: { place: '강남역', durationHours: 2 },
    recommendations: [recommendation()],
    ...overrides,
  })

  it('후보가 있는 응답과 빈 배열 응답을 받아들인다', () => {
    expect(isRecommendationResponse(body())).toBe(true)
    expect(isRecommendationResponse(body({ recommendations: [] }))).toBe(true)
  })

  it.each([
    ['query 누락', body({ query: undefined })],
    ['durationHours가 0', body({ query: { place: '강남역', durationHours: 0 } })],
    ['durationHours가 문자열', body({ query: { place: '강남역', durationHours: '2' } })],
    ['recommendations가 배열이 아님', body({ recommendations: {} })],
    ['추천 하나가 형식 위반', body({ recommendations: [recommendation({ rank: 'X' })] })],
    ['null', null],
  ])('거부한다: %s', (_label, value) => {
    expect(isRecommendationResponse(value)).toBe(false)
  })
})

describe('isReservation', () => {
  it('네 가지 status를 모두 받아들인다', () => {
    for (const status of ['PENDING_PAYMENT', 'CONFIRMED', 'PAYMENT_FAILED', 'CANCELLED']) {
      expect(isReservation(reservation({ status }))).toBe(true)
    }
  })

  it('선택 필드(message 등)가 있어도 받아들이고 모르는 필드는 무시한다', () => {
    expect(isReservation(reservation({ message: '예약이 완료되었습니다', extra: 1 }))).toBe(true)
  })

  it.each([
    ['알 수 없는 status', reservation({ status: 'REFUNDED' })],
    ['정수 아닌 version', reservation({ version: 1.5 })],
    ['문자열 version', reservation({ version: '1' })],
    ['version 누락', reservation({ version: undefined })],
    ['reservationId 누락', reservation({ reservationId: undefined })],
    ['lotName 누락', reservation({ lotName: undefined })],
    ['amount 누락', reservation({ amount: undefined })],
    ['durationHours 누락', reservation({ durationHours: undefined })],
    ['paymentRequestedAt 누락', reservation({ paymentRequestedAt: undefined })],
    ['message가 문자열이 아님', reservation({ message: 5 })],
    ['배열', []],
    ['undefined', undefined],
  ])('거부한다: %s', (_label, value) => {
    expect(isReservation(value)).toBe(false)
  })
})

describe('isApiError', () => {
  it('code와 message가 있는 오류 본문을 받아들이고 reservationId를 허용한다', () => {
    expect(isApiError({ code: 'MISSING_PLACE', message: '장소를 알려 주세요' })).toBe(true)
    expect(isApiError({ code: 'PENDING_EXISTS', message: 'm', reservationId: 'r1' })).toBe(true)
  })

  it.each([
    ['code 누락', { message: 'm' }],
    ['code가 숫자', { code: 1, message: 'm' }],
    ['reservationId가 숫자', { code: 'X', message: 'm', reservationId: 5 }],
    ['null', null],
  ])('거부한다: %s', (_label, value) => {
    expect(isApiError(value)).toBe(false)
  })
})

describe('isPaymentAccepted', () => {
  const accepted = (overrides = {}) => ({
    paymentId: 'pay1',
    reservationId: 'r1',
    status: 'PENDING_PAYMENT',
    paymentRequestedAt: '2026-11-08T01:00:00Z',
    ...overrides,
  })

  it('결제 접수 응답을 받아들인다', () => {
    expect(isPaymentAccepted(accepted())).toBe(true)
    expect(isPaymentAccepted(accepted({ status: 'CONFIRMED' }))).toBe(true)
  })

  it.each([
    ['reservationId 누락', accepted({ reservationId: undefined })],
    ['알 수 없는 status', accepted({ status: 'X' })],
    ['paymentRequestedAt 누락', accepted({ paymentRequestedAt: undefined })],
    ['null', null],
  ])('거부한다: %s', (_label, value) => {
    expect(isPaymentAccepted(value)).toBe(false)
  })
})

describe('상태 값 매핑', () => {
  it('status를 화면 상태로 바꾼다', () => {
    expect(statusToView('PENDING_PAYMENT')).toBe('pending')
    expect(statusToView('CONFIRMED')).toBe('confirmed')
    expect(statusToView('PAYMENT_FAILED')).toBe('failed')
    expect(statusToView('CANCELLED')).toBe('cancelled')
  })

  it('알 수 없는 status는 null이다', () => {
    expect(statusToView('REFUNDED')).toBeNull()
    expect(statusToView(undefined)).toBeNull()
  })

  it('종료 상태만 종료로 본다', () => {
    expect(isTerminalStatus('CONFIRMED')).toBe(true)
    expect(isTerminalStatus('PAYMENT_FAILED')).toBe(true)
    expect(isTerminalStatus('CANCELLED')).toBe(true)
    expect(isTerminalStatus('PENDING_PAYMENT')).toBe(false)
    expect(isTerminalStatus('nope')).toBe(false)
  })
})

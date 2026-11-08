import { describe, it, expect, vi, afterEach } from 'vitest'
import { createPayment, ApiError } from '../src/api.js'

afterEach(() => vi.unstubAllGlobals())

describe('api 결제 오류 처리', () => {
  it('402 + {code, message} 응답을 ApiError(code, message)로 던진다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 402,
      json: async () => ({ code: 'PAYMENT_VALIDATION_FAILED', message: 'amount는 1 이상이어야 합니다' })
    }))
    const err = await createPayment('r1', 0).catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(402)
    expect(err.code).toBe('PAYMENT_VALIDATION_FAILED')
    expect(err.message).toBe('amount는 1 이상이어야 합니다')
  })

  it('code가 없는 오류 응답은 code가 null이다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => null
    }))
    const err = await createPayment('r1', 100).catch((e) => e)
    expect(err.code).toBeNull()
    expect(err.message).toContain('500')
  })
})

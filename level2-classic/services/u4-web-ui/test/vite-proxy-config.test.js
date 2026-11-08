import { describe, expect, it, vi } from 'vitest'
import {
  DEFAULT_HOST,
  DEFAULT_PORT,
  PROXY_TIMEOUT_MS,
  assertLoopbackHost,
  bypass,
  createProxyConfig,
  resolveHost,
  resolveTargets,
} from '../vite.proxy.js'

describe('프록시 대상 주소 (NFR8.1)', () => {
  it('환경 변수가 없으면 기본 주소를 쓴다', () => {
    expect(resolveTargets({})).toEqual({
      u1: 'http://127.0.0.1:8081',
      u2: 'http://127.0.0.1:8082',
      u3: 'http://127.0.0.1:8083',
    })
  })

  it('U1_URL, U2_URL, U3_URL로 바꿀 수 있다', () => {
    expect(
      resolveTargets({ U1_URL: 'http://a:1', U2_URL: 'http://b:2', U3_URL: 'http://c:3' }),
    ).toEqual({ u1: 'http://a:1', u2: 'http://b:2', u3: 'http://c:3' })
  })

  it('빈 문자열 환경 변수는 기본값으로 돌린다', () => {
    expect(resolveTargets({ U1_URL: '' }).u1).toBe('http://127.0.0.1:8081')
  })
})

describe('프록시 설정 값', () => {
  const config = createProxyConfig({})

  it('정확히 세 경로만 연결한다 (/internal과 웹훅 경로 키는 없다)', () => {
    expect(Object.keys(config)).toEqual(['/api/search', '/api/reservations', '^/api/payments$'])
    for (const key of Object.keys(config)) {
      expect(key).not.toContain('internal')
      expect(key).not.toContain('webhook')
    }
  })

  it('/api/search는 u1로 간다', () => {
    expect(config['/api/search']).toMatchObject({
      target: 'http://127.0.0.1:8081',
      changeOrigin: true,
    })
  })

  it('/api/reservations는 u2로 가고 SSE를 변환하지 않는다 (타임아웃 600000ms, identity)', () => {
    const sse = config['/api/reservations']
    expect(sse.target).toBe('http://127.0.0.1:8082')
    expect(sse.timeout).toBe(600000)
    expect(sse.proxyTimeout).toBe(600000)
    expect(PROXY_TIMEOUT_MS).toBe(600000)
    expect(sse.headers['Accept-Encoding']).toBe('identity')
    expect(sse.rewrite).toBeUndefined()
    expect(sse.selfHandleResponse).toBeUndefined()
    expect(sse.bypass).toBeUndefined()
  })

  it('결제는 경로 끝을 고정한 키로 u3에 연결하고 bypass를 단다', () => {
    const payments = config['^/api/payments$']
    expect(payments.target).toBe('http://127.0.0.1:8083')
    expect(payments.bypass).toBe(bypass)
    const key = new RegExp('^/api/payments$')
    expect(key.test('/api/payments')).toBe(true)
    expect(key.test('/api/payments/')).toBe(false)
    expect(key.test('/api/payments/webhook')).toBe(false)
    expect(key.test('/api/payments?x=1')).toBe(false)
  })

  it('환경 변수의 대상 주소를 반영한다', () => {
    const custom = createProxyConfig({
      U1_URL: 'http://h:1',
      U2_URL: 'http://h:2',
      U3_URL: 'http://h:3',
    })
    expect(custom['/api/search'].target).toBe('http://h:1')
    expect(custom['/api/reservations'].target).toBe('http://h:2')
    expect(custom['^/api/payments$'].target).toBe('http://h:3')
  })
})

describe('bypass (결제 경로의 메서드·쿼리 제한)', () => {
  it('쿼리 없는 POST만 프록시한다 (undefined)', () => {
    expect(bypass({ method: 'POST', url: '/api/payments' })).toBeUndefined()
  })

  it.each(['GET', 'HEAD', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'])(
    '%s는 프록시하지 않는다 (false)',
    (method) => {
      expect(bypass({ method, url: '/api/payments' })).toBe(false)
    },
  )

  it('쿼리 문자열이 붙은 요청은 프록시하지 않는다', () => {
    expect(bypass({ method: 'POST', url: '/api/payments?x=1' })).toBe(false)
    expect(bypass({ method: 'POST', url: '/api/payments?' })).toBe(false)
  })

  it('정확한 경로가 아니면 키에 걸려 와도 프록시하지 않는다 (방어)', () => {
    expect(bypass({ method: 'POST', url: '/api/payments/' })).toBe(false)
    expect(bypass({ method: 'POST', url: '/api/payments/webhook' })).toBe(false)
  })

  it('메서드나 url이 없는 요청도 던지지 않고 거절한다', () => {
    expect(bypass({})).toBe(false)
    expect(bypass({ method: 'post', url: '/api/payments' })).toBeUndefined()
  })
})

describe('호스트 (NFR6.6)', () => {
  it('기본 호스트는 127.0.0.1이고 포트는 5173이다', () => {
    expect(DEFAULT_HOST).toBe('127.0.0.1')
    expect(DEFAULT_PORT).toBe(5173)
    expect(resolveHost({})).toBe('127.0.0.1')
  })

  it('UI_HOST로 바꿀 수 있다', () => {
    expect(resolveHost({ UI_HOST: 'localhost' })).toBe('localhost')
    expect(resolveHost({ UI_HOST: '' })).toBe('127.0.0.1')
  })
})

describe('assertLoopbackHost (F5 값 목록)', () => {
  it.each(['127.0.0.1', 'localhost', 'LOCALHOST', 'Localhost', '::1', '[::1]'])(
    '루프백 %s는 허용한다',
    (host) => {
      const warn = vi.fn()
      expect(() => assertLoopbackHost(host, {}, warn)).not.toThrow()
      expect(warn).not.toHaveBeenCalled()
    },
  )

  it.each([
    ['0.0.0.0', '0.0.0.0'],
    ['true (--host만 준 경우)', true],
    ['undefined', undefined],
    ['빈 문자열', ''],
    ['공백', '   '],
    ['외부 IP', '203.0.113.7'],
    ['사설 IP', '192.168.0.5'],
    ['::', '::'],
    ['호스트 이름', 'example.com'],
    ['false', false],
    ['숫자', 5],
  ])('비루프백 %s는 거부한다', (_label, host) => {
    expect(() => assertLoopbackHost(host, {}, vi.fn())).toThrow(/루프백/)
  })

  it('UI_ALLOW_NON_LOOPBACK=1이면 경고와 함께 허용한다', () => {
    const warn = vi.fn()
    expect(() => assertLoopbackHost('0.0.0.0', { UI_ALLOW_NON_LOOPBACK: '1' }, warn)).not.toThrow()
    expect(warn).toHaveBeenCalledTimes(1)
    expect(warn.mock.calls[0][0]).toMatch(/UI_ALLOW_NON_LOOPBACK/)
  })

  it('UI_ALLOW_NON_LOOPBACK가 1이 아니면 허용하지 않는다', () => {
    for (const value of ['0', 'true', '', 'yes']) {
      expect(() =>
        assertLoopbackHost('0.0.0.0', { UI_ALLOW_NON_LOOPBACK: value }, vi.fn()),
      ).toThrow()
    }
  })

  it('거부 메시지에 주소와 해결 방법이 들어 있다', () => {
    expect(() => assertLoopbackHost('10.1.2.3', {}, vi.fn())).toThrow(/10\.1\.2\.3/)
  })
})

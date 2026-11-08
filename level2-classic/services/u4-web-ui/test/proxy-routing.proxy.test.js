// L2 프록시 통합 시험: 실제 Vite createServer가 vite.config.js(와 vite.proxy.js)를 읽어 기동한다.
// 합격 기준은 상태 코드가 아니라 시험용 서비스가 받은 요청 수다 (Vite가 SPA 대체 응답으로 200을 줄 수 있다).
import { spawn } from 'node:child_process'
import path from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import {
  PROJECT_ROOT,
  occupyPort,
  request,
  sleep,
  startRecorder,
  startVite,
} from './support/servers.js'

let u1
let u2
let u3
let web

const hitCounts = () => ({ u1: u1.hits.length, u2: u2.hits.length, u3: u3.hits.length })

async function settleAndCount() {
  await sleep(150)
  return hitCounts()
}

beforeAll(async () => {
  u1 = await startRecorder('u1')
  u2 = await startRecorder('u2')
  u3 = await startRecorder('u3')
  web = await startVite({ env: { U1_URL: u1.url, U2_URL: u2.url, U3_URL: u3.url } })
})

afterAll(async () => {
  await web?.close()
  await Promise.all([u1?.close(), u2?.close(), u3?.close()])
})

describe('정상 경로는 해당 서비스에 닿는다', () => {
  it('POST /api/payments는 u3에 한 건 닿는다', async () => {
    const before = u3.hits.length
    await request(web.port, {
      method: 'POST',
      path: '/api/payments',
      headers: { 'Content-Type': 'application/json', 'X-Session-Id': 'abc' },
      body: JSON.stringify({ passId: 'p1' }),
    })
    await sleep(100)
    expect(u3.hits.length).toBe(before + 1)
    expect(u3.hits.at(-1)).toEqual({ method: 'POST', url: '/api/payments' })
  })

  it('/api/search/...는 u1에, /api/reservations/...는 u2에 닿는다', async () => {
    const before = hitCounts()
    await request(web.port, {
      method: 'POST',
      path: '/api/search/recommendations',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    })
    await request(web.port, {
      path: '/api/reservations/current',
      headers: { 'X-Session-Id': 'abc' },
    })
    const after = await settleAndCount()
    expect(after.u1).toBe(before.u1 + 1)
    expect(after.u2).toBe(before.u2 + 1)
    expect(u1.hits.at(-1).url).toBe('/api/search/recommendations')
    expect(u2.hits.at(-1).url).toBe('/api/reservations/current')
  })
})

describe('결제 경로의 정확 일치 (웹훅 차단)', () => {
  const blocked = [
    ['POST', '/api/payments/webhook'],
    ['POST', '/api/payments/webhook/'],
    ['POST', '/api/payments/'],
    ['GET', '/api/payments'],
    ['POST', '/api/payments?x=1'],
    ['PUT', '/api/payments'],
    ['DELETE', '/api/payments'],
  ]

  it.each(blocked)('%s %s는 u3 수신이 0이다', async (method, requestPath) => {
    const before = u3.hits.length
    await request(web.port, {
      method,
      path: requestPath,
      headers: { 'Content-Type': 'application/json', 'X-Signature': 'sha256=00' },
      body: method === 'GET' || method === 'DELETE' ? undefined : '{}',
    })
    await sleep(150)
    expect(u3.hits.length).toBe(before)
  })
})

describe('서비스 간 호출 경로는 연결하지 않는다', () => {
  const internal = [
    ['POST', '/internal/reservations'],
    ['GET', '/internal/passes/p1?durationHours=2'],
    ['POST', '/internal/reservations/r1/payment-result'],
    ['GET', '/internal/passes/x'],
  ]

  it.each(internal)('%s %s는 시험용 u1·u2·u3 모두 수신이 0이다', async (method, requestPath) => {
    const before = hitCounts()
    await request(web.port, {
      method,
      path: requestPath,
      headers: { 'Content-Type': 'application/json' },
      body: method === 'GET' ? undefined : '{}',
    })
    expect(await settleAndCount()).toEqual(before)
  })
})

describe('서버 설정', () => {
  it('SSE 경로의 시간 초과 설정 값이 해석된 Vite 설정에 그대로 들어 있다', () => {
    const proxy = web.vite.config.server.proxy
    expect(proxy['/api/reservations'].timeout).toBe(600000)
    expect(proxy['/api/reservations'].proxyTimeout).toBe(600000)
    expect(proxy['/api/reservations'].headers['Accept-Encoding']).toBe('identity')
    expect(web.vite.config.server.strictPort).toBeDefined()
  })

  it('호스트가 비루프백이면 시작을 거부한다 (0.0.0.0, --host 값 true)', async () => {
    await expect(startVite({ server: { host: '0.0.0.0' } })).rejects.toThrow(/루프백/)
    await expect(startVite({ server: { host: true } })).rejects.toThrow(/루프백/)
  })

  it('UI_ALLOW_NON_LOOPBACK=1을 명시하면 경고와 함께 허용한다', async () => {
    const allowed = await startVite({
      env: { UI_ALLOW_NON_LOOPBACK: '1' },
      server: { host: '0.0.0.0' },
    })
    await allowed.close()
  })

  it('CLI --host 0.0.0.0으로 시작하면 프로세스가 거부하며 끝난다', async () => {
    const child = spawn(
      process.execPath,
      [
        path.join(PROJECT_ROOT, 'node_modules/vite/bin/vite.js'),
        '--host',
        '0.0.0.0',
        '--port',
        '0',
      ],
      {
        cwd: PROJECT_ROOT,
        env: { ...process.env, UI_ALLOW_NON_LOOPBACK: '' },
        stdio: ['ignore', 'pipe', 'pipe'],
      },
    )
    let output = ''
    child.stdout.on('data', (chunk) => (output += chunk))
    child.stderr.on('data', (chunk) => (output += chunk))
    const exit = await new Promise((resolve) => {
      const timer = setTimeout(() => {
        child.kill('SIGKILL')
        resolve({ code: 'timeout' })
      }, 30000)
      child.on('exit', (code) => {
        clearTimeout(timer)
        resolve({ code })
      })
    })
    expect(exit.code).not.toBe('timeout')
    expect(exit.code).not.toBe(0)
    expect(output).toMatch(/루프백/)
  })

  it('포트가 이미 쓰이면 다른 포트로 올라가지 않고 실패한다', async () => {
    const occupied = await occupyPort()
    try {
      await expect(
        startVite({ server: { host: '127.0.0.1', port: occupied.port, strictPort: true } }),
      ).rejects.toThrow(/in use|already/i)
    } finally {
      await occupied.close()
    }
  })
})

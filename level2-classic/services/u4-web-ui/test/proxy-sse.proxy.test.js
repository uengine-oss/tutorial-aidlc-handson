// L2 SSE 전달 시험: 같은 이벤트를 직접 수신과 프록시 경유로 동시에 받아 이벤트별 수신 시각 차이로 판정한다.
// 이벤트 120개(50ms 간격) 중 앞 20개를 예열로 버리고 뒤 100개의 p99 <= 100ms, 최댓값 <= 200ms.
import http from 'node:http'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { LOOPBACK, startSseServer, startVite } from './support/servers.js'
import { WARMUP_RUNS, dropWarmup, judge } from './support/stats.js'

const EVENT_COUNT = 120
const INTERVAL_MS = 50
const LIMIT_MS = 100
const BUNCH_THRESHOLD_MS = INTERVAL_MS / 2

let sse
let web

beforeAll(async () => {
  sse = await startSseServer({ count: EVENT_COUNT, intervalMs: INTERVAL_MS })
  web = await startVite({ env: { U2_URL: sse.url } })
})

afterAll(async () => {
  await web?.close()
  await sse?.close()
})

/** SSE 연결을 열어 이벤트 수신 시각(performance.now())을 모은다. */
function collect(port, via) {
  return new Promise((resolve, reject) => {
    const received = []
    let headers = null
    const req = http.get(
      {
        host: LOOPBACK,
        port,
        path: `/api/reservations/r1/events?sessionId=test&via=${via}`,
        // 압축을 요청해도 프록시는 업스트림에 identity를 보내야 한다
        headers: { Accept: 'text/event-stream', 'Accept-Encoding': 'gzip' },
      },
      (res) => {
        headers = res.headers
        let buffer = ''
        res.setEncoding('utf8')
        res.on('data', (chunk) => {
          buffer += chunk
          let boundary = buffer.indexOf('\n\n')
          while (boundary !== -1) {
            const block = buffer.slice(0, boundary)
            buffer = buffer.slice(boundary + 2)
            const dataLine = block.split('\n').find((line) => line.startsWith('data: '))
            if (dataLine) {
              const data = JSON.parse(dataLine.slice(6))
              received.push({ i: data.i, sentAt: data.sentAt, receivedAt: performance.now() })
            }
            boundary = buffer.indexOf('\n\n')
          }
        })
        res.on('end', () => resolve({ received, headers }))
        res.on('error', reject)
      },
    )
    req.on('error', reject)
  })
}

describe('프록시를 거친 SSE 전달 (NFR1.2)', () => {
  it('이벤트가 버퍼링 없이 전달되고 직접 수신과의 지연 차이가 한도 안이다', async () => {
    const [direct, proxied] = await Promise.all([
      collect(sse.port, 'direct'),
      collect(web.port, 'proxy'),
    ])

    // 이벤트 개수는 직접 수신과 같아야 한다
    expect(direct.received).toHaveLength(EVENT_COUNT)
    expect(proxied.received).toHaveLength(direct.received.length)

    // 응답은 압축되지 않았고, 업스트림에는 identity가 갔다
    expect(proxied.headers['content-encoding']).toBeUndefined()
    expect(proxied.headers['content-type']).toMatch(/text\/event-stream/)
    const upstream = Object.fromEntries(sse.requests.map((r) => [r.via, r.acceptEncoding]))
    expect(upstream.proxy).toBe('identity')
    expect(upstream.direct).toBe('gzip')

    // 이벤트별 수신 시각 차이 (프록시 경유 - 직접). 앞 20개는 예열로 버린다.
    const deltas = proxied.received.map(
      (event, index) => event.receivedAt - direct.received[index].receivedAt,
    )
    const measured = dropWarmup(deltas, WARMUP_RUNS)
    expect(measured).toHaveLength(EVENT_COUNT - WARMUP_RUNS)
    const result = judge('프록시 SSE 전달 지연 차이', measured, LIMIT_MS, 2)
    expect(result.p99).toBeLessThanOrEqual(result.limitMs)
    expect(result.max).toBeLessThanOrEqual(result.maxLimit)

    // 묶임 판정: 직접 수신은 25ms 이상 벌어졌는데 프록시 경유에서 25ms 미만으로 붙어 도착한 쌍이 있으면 실패
    const bunched = []
    for (let i = 1; i < proxied.received.length; i += 1) {
      const proxyGap = proxied.received[i].receivedAt - proxied.received[i - 1].receivedAt
      const directGap = direct.received[i].receivedAt - direct.received[i - 1].receivedAt
      if (proxyGap < BUNCH_THRESHOLD_MS && directGap >= BUNCH_THRESHOLD_MS) {
        bunched.push({ i, proxyGap, directGap })
      }
    }
    expect(bunched).toEqual([])
  })
})

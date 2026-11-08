// 프록시 통합 시험용 서버 도구: 요청 수를 세는 시험용 서비스, 시험용 SSE 서버, 임의 포트의 실제 Vite 개발 서버.
import http from 'node:http'
import net from 'node:net'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'

export const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
export const LOOPBACK = '127.0.0.1'

export function listen(server, port = 0, host = LOOPBACK) {
  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(port, host, () => {
      server.off('error', reject)
      resolve(server.address().port)
    })
  })
}

export function closeServer(server) {
  return new Promise((resolve) => {
    server.closeAllConnections?.()
    server.close(() => resolve())
  })
}

/** 받은 요청을 세는 시험용 서비스. */
export async function startRecorder(name) {
  const hits = []
  const server = http.createServer((req, res) => {
    hits.push({ method: req.method, url: req.url })
    req.resume()
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ service: name }))
  })
  const port = await listen(server)
  return {
    name,
    hits,
    port,
    url: `http://${LOOPBACK}:${port}`,
    close: () => closeServer(server),
  }
}

/**
 * 시험용 SSE 서버. 두 연결(직접 수신, 프록시 경유)이 모이면 같은 이벤트를 intervalMs 간격으로 count개 보낸다.
 * 이벤트 데이터에 보낸 시각(performance.now())을 담는다.
 */
export async function startSseServer({ count = 120, intervalMs = 50, expectedClients = 2 } = {}) {
  const clients = []
  const requests = []
  let started = false

  function emitAll() {
    let sent = 0
    const timer = setInterval(() => {
      const data = JSON.stringify({ i: sent, sentAt: performance.now() })
      for (const client of clients) {
        client.write(`id: ${sent}\nevent: snapshot\ndata: ${data}\n\n`)
      }
      sent += 1
      if (sent >= count) {
        clearInterval(timer)
        setTimeout(() => {
          for (const client of clients) {
            client.end()
          }
        }, 200)
      }
    }, intervalMs)
  }

  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost')
    requests.push({
      via: url.searchParams.get('via'),
      acceptEncoding: req.headers['accept-encoding'],
    })
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    })
    res.write(': connected\n\n')
    clients.push(res)
    if (!started && clients.length >= expectedClients) {
      started = true
      setTimeout(emitAll, 100)
    }
  })
  const port = await listen(server)
  return { port, url: `http://${LOOPBACK}:${port}`, requests, close: () => closeServer(server) }
}

/** 실제 Vite 개발 서버를 임의 포트로 띄운다. env는 vite.config.js가 읽는 프록시 대상 환경 변수. */
export async function startVite({ env = {}, server: serverOverrides = {} } = {}) {
  const previous = {}
  for (const [key, value] of Object.entries(env)) {
    previous[key] = process.env[key]
    process.env[key] = value
  }
  const restoreEnv = () => {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) {
        delete process.env[key]
      } else {
        process.env[key] = value
      }
    }
  }

  let vite
  try {
    vite = await createServer({
      root: PROJECT_ROOT,
      configFile: path.join(PROJECT_ROOT, 'vite.config.js'),
      logLevel: 'silent',
      server: { port: 0, strictPort: false, ...serverOverrides },
    })
    await vite.listen()
  } catch (error) {
    restoreEnv()
    throw error
  }
  const port = vite.httpServer.address().port
  return {
    vite,
    port,
    close: async () => {
      await vite.close()
      restoreEnv()
    },
  }
}

/** 단순 HTTP 요청. 응답 본문을 문자열로 돌려준다. */
export function request(
  port,
  { method = 'GET', path: requestPath = '/', headers = {}, body } = {},
) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      { host: LOOPBACK, port, method, path: requestPath, headers },
      (res) => {
        const chunks = []
        res.on('data', (chunk) => chunks.push(chunk))
        res.on('end', () =>
          resolve({
            status: res.statusCode,
            headers: res.headers,
            body: Buffer.concat(chunks).toString('utf8'),
          }),
        )
      },
    )
    req.on('error', reject)
    req.setTimeout(10000, () => req.destroy(new Error('요청 시간 초과')))
    if (body !== undefined) {
      req.write(body)
    }
    req.end()
  })
}

/** 이미 쓰는 포트를 하나 만든다. */
export async function occupyPort() {
  const server = net.createServer()
  const port = await listen(server)
  return { port, close: () => closeServer(server) }
}

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

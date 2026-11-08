'use strict'
// 가짜 서버. run-local 시험(상태 확인, u4 준비 점검)과 smoke-local 시험(추천 -> 결제 -> 예약)이 함께 쓴다.
// 동작은 환경 변수가 정하고, FAKE_CTL_DIR 안에 같은 이름(소문자) 파일이 있으면 요청마다 그 값이 우선한다.
//   FAKE_ROLE, FAKE_PORT, FAKE_HEALTH(UP|DOWN), FAKE_IGNORE_TERM
//   FAKE_ROOT_STATUS(200), FAKE_RECS_STATUS(400, 본문이 {}일 때), FAKE_SEARCH_STATUS(200, 정상 요청)
//   FAKE_PAYMENT_STATUS(202), FAKE_WEBHOOK_STATUS(404), FAKE_INTERNAL_STATUS(404)
//   FAKE_RESERVATION_FINAL(CONFIRMED), FAKE_CONFIRM_DELAY_MS(0)
//   FAKE_LOG_U2, FAKE_LOG_U3: 요청 줄을 덧붙일 파일. FAKE_LOG_GUARDED=1 이면 웹훅·내부 경로 요청도 남긴다.
const http = require('http')
const fs = require('fs')

const port = Number(process.env.FAKE_PORT)
const host = '127.0.0.1'
const ctlDir = process.env.FAKE_CTL_DIR || ''

if (process.env.FAKE_IGNORE_TERM === '1') {
  process.on('SIGTERM', () => {})
}

function setting(name, fallback) {
  const key = name.toLowerCase()
  if (ctlDir) {
    try {
      const value = fs.readFileSync(`${ctlDir}/${key}`, 'utf8').trim()
      if (value) {
        return value
      }
    } catch (error) {
      // 제어 파일이 없으면 환경 변수 값을 쓴다.
    }
  }
  return process.env[name] || fallback
}

let paidAt = 0

function appendLog(file, line) {
  if (!file) {
    return
  }
  try {
    fs.appendFileSync(file, `${line}\n`)
  } catch (error) {
    // 로그 기록 실패는 시험 서버의 동작에 영향을 주지 않는다.
  }
}

function send(res, status, body) {
  const text = typeof body === 'string' ? body : JSON.stringify(body)
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(text)
}

function route(req, res, raw) {
  const path = req.url.split('?')[0]
  if (req.method === 'GET' && path === '/actuator/health') {
    return send(res, 200, { status: setting('FAKE_HEALTH', 'UP') })
  }
  if (req.method === 'GET' && path === '/') {
    return send(res, Number(setting('FAKE_ROOT_STATUS', '200')), '{"page":"fake-ui"}')
  }
  if (req.method === 'POST' && path === '/api/search/recommendations') {
    let text = ''
    try {
      text = JSON.parse(raw || '{}').text || ''
    } catch (error) {
      text = ''
    }
    if (!text) {
      return send(res, Number(setting('FAKE_RECS_STATUS', '400')), { code: 'INVALID_TEXT', message: '입력이 비어 있음' })
    }
    const status = Number(setting('FAKE_SEARCH_STATUS', '200'))
    if (status !== 200) {
      return send(res, status, { code: 'UPSTREAM', message: '실패' })
    }
    return send(res, 200, {
      query: { place: '강남역', durationHours: 2 },
      recommendations: [
        { passId: 'pass-best-1', lotName: '시험 주차장', price: 4000, distanceMeters: 120, score: 91, rank: 'BEST', reason: { topFactor: 'PRICE', text: '가장 저렴' } },
        { passId: 'pass-cand-2', lotName: '후보 주차장', price: 5000, distanceMeters: 300, score: 70, rank: 'CANDIDATE', reason: { topFactor: 'DISTANCE', text: '가까움' } },
      ],
    })
  }
  if (req.method === 'POST' && path === '/api/payments') {
    const status = Number(setting('FAKE_PAYMENT_STATUS', '202'))
    if (status === 202) {
      paidAt = Date.now()
      return send(res, 202, { paymentId: 'pay-1', reservationId: 'res-1', status: 'PENDING_PAYMENT', paymentRequestedAt: new Date().toISOString() })
    }
    return send(res, status, { code: 'UPSTREAM_FAILED', message: '결제 실패' })
  }
  if (req.method === 'GET' && path === '/api/reservations/current') {
    if (!paidAt) {
      return send(res, 404, { code: 'NO_RESERVATION', message: '예약 없음' })
    }
    const delay = Number(setting('FAKE_CONFIRM_DELAY_MS', '0'))
    const status = Date.now() - paidAt >= delay ? setting('FAKE_RESERVATION_FINAL', 'CONFIRMED') : 'PENDING_PAYMENT'
    return send(res, 200, { reservationId: 'res-1', version: 2, status, lotName: '시험 주차장', durationHours: 2, amount: 4000, paymentRequestedAt: new Date(paidAt).toISOString() })
  }
  if (req.method === 'POST' && path === '/api/payments/webhook') {
    return send(res, Number(setting('FAKE_WEBHOOK_STATUS', '404')), { code: 'NOT_FOUND', message: '없음' })
  }
  if (req.method === 'POST' && path === '/internal/reservations') {
    return send(res, Number(setting('FAKE_INTERNAL_STATUS', '404')), { code: 'NOT_FOUND', message: '없음' })
  }
  return send(res, 404, { code: 'NOT_FOUND', message: '없음' })
}

const server = http.createServer((req, res) => {
  const chunks = []
  req.on('data', (chunk) => chunks.push(chunk))
  req.on('end', () => {
    const path = req.url.split('?')[0]
    const guardedWebhook = path === '/api/payments/webhook'
    const guardedInternal = path === '/internal/reservations'
    const logGuarded = setting('FAKE_LOG_GUARDED', '') === '1'
    const line = `REQ ${req.method} ${path}`
    appendLog(ctlDir && `${ctlDir}/requests.log`, `${line} sid=${req.headers['x-session-id'] || '-'} ct=${req.headers['content-type'] || '-'}`)
    if (guardedWebhook) {
      if (logGuarded) {
        appendLog(process.env.FAKE_LOG_U3, line)
      }
    } else if (guardedInternal) {
      if (logGuarded) {
        appendLog(process.env.FAKE_LOG_U2, line)
      }
    } else {
      appendLog(process.env.FAKE_LOG_U3, line)
      appendLog(process.env.FAKE_LOG_U2, line)
    }
    route(req, res, Buffer.concat(chunks).toString('utf8'))
  })
})

server.on('error', (error) => {
  process.stderr.write(`fake-server 오류: ${error.message}\n`)
  process.exit(1)
})

server.listen(port, host)

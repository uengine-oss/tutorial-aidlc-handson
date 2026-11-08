// 프록시 설정 값과 호스트 검사. 순수 함수와 값만 내보내 시험이 직접 읽는다 (vite.config.js가 연결한다).

export const DEFAULT_HOST = '127.0.0.1'
export const DEFAULT_PORT = 5173

/** SSE가 오래 열려 있으므로 예약 자동 취소(180초)보다 충분히 긴 10분. timeout과 proxyTimeout에 같은 값을 쓴다. */
export const PROXY_TIMEOUT_MS = 600000

export const PAYMENTS_PATH = '/api/payments'

const DEFAULT_TARGETS = {
  u1: 'http://127.0.0.1:8081',
  u2: 'http://127.0.0.1:8082',
  u3: 'http://127.0.0.1:8083',
}

const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '::1'])

/** 서비스 주소. 환경 변수로 바꿀 수 있고 기본값만 코드에 둔다 (NFR8.1). */
export function resolveTargets(env = process.env) {
  return {
    u1: env.U1_URL || DEFAULT_TARGETS.u1,
    u2: env.U2_URL || DEFAULT_TARGETS.u2,
    u3: env.U3_URL || DEFAULT_TARGETS.u3,
  }
}

export function resolveHost(env = process.env) {
  return env.UI_HOST || DEFAULT_HOST
}

/**
 * 결제 경로 `^/api/payments$`에 거는 bypass. Vite 프록시 키는 메서드를 가리지 못한다.
 * false는 프록시하지 않고 404로 끝내는 것이고, undefined는 그대로 프록시하는 것이다.
 * POST가 아니거나 쿼리 문자열이 붙었거나 정확한 경로가 아니면 u3에 보내지 않는다 (NFR6.3).
 */
export function bypass(req) {
  if (String(req.method ?? '').toUpperCase() !== 'POST') {
    return false
  }
  if (req.url !== PAYMENTS_PATH) {
    return false
  }
  return undefined
}

/**
 * Vite server.proxy 값. 연결하는 경로는 이 세 가지뿐이다.
 * `/internal/**`와 `/api/payments/webhook`에는 키를 두지 않는다 (서명 검증이 마지막 방어선이다).
 */
export function createProxyConfig(env = process.env) {
  const targets = resolveTargets(env)
  return {
    '/api/search': { target: targets.u1, changeOrigin: true },
    // SSE: 응답을 변환하지 않고 그대로 흘린다. 압축으로 이벤트가 모이지 않도록 identity를 고정한다.
    '/api/reservations': {
      target: targets.u2,
      changeOrigin: true,
      timeout: PROXY_TIMEOUT_MS,
      proxyTimeout: PROXY_TIMEOUT_MS,
      headers: { 'Accept-Encoding': 'identity' },
    },
    [`^${PAYMENTS_PATH}$`]: { target: targets.u3, changeOrigin: true, bypass },
  }
}

function normalizeHost(host) {
  if (typeof host !== 'string') {
    return null
  }
  const trimmed = host.trim().toLowerCase()
  const unbracketed =
    trimmed.startsWith('[') && trimmed.endsWith(']') ? trimmed.slice(1, -1) : trimmed
  return unbracketed === '' ? null : unbracketed
}

/**
 * Vite가 설정을 해석한 뒤의 server.host를 검사한다 (CLI --host가 설정 파일 값을 덮어써도 잡힌다).
 * 루프백이 아니면 던진다. 환경 변수 UI_ALLOW_NON_LOOPBACK=1을 명시했을 때만 경고와 함께 허용한다 (NFR6.6).
 */
export function assertLoopbackHost(
  host,
  env = process.env,
  warn = (...args) => console.warn(...args),
) {
  const normalized = normalizeHost(host)
  if (normalized !== null && LOOPBACK_HOSTS.has(normalized)) {
    return normalized
  }
  const shown = host === undefined ? '(지정 안 됨)' : String(host)
  if (env.UI_ALLOW_NON_LOOPBACK === '1') {
    warn(
      `경고: 개발 서버 호스트 ${shown}는 루프백이 아닙니다. UI_ALLOW_NON_LOOPBACK=1이 설정되어 허용합니다. 같은 네트워크의 다른 기기가 접근할 수 있습니다.`,
    )
    return normalized ?? shown
  }
  throw new Error(
    `개발 서버 호스트 ${shown}는 루프백이 아니어서 시작하지 않습니다. 127.0.0.1, localhost, ::1만 허용합니다. 꼭 필요하면 UI_ALLOW_NON_LOOPBACK=1을 명시하세요.`,
  )
}

import { isRecommendationResponse } from '../lib/guards.js'

export const SESSION_KEY = 'vp.sessionId'
export const RECOMMENDATIONS_KEY = 'vp.recommendations'
export const LAST_PAID_KEY = 'vp.lastPaidPassId'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * UUID v4. crypto.randomUUID가 없으면(비보안 컨텍스트 등) getRandomValues로 만든다 (NFR6.4).
 * @param {{ randomUUID?: () => string, getRandomValues?: (bytes: Uint8Array) => Uint8Array }} [cryptoObj]
 */
export function generateUuid(cryptoObj = globalThis.crypto) {
  if (typeof cryptoObj?.randomUUID === 'function') {
    return cryptoObj.randomUUID()
  }
  if (typeof cryptoObj?.getRandomValues !== 'function') {
    throw new Error('안전한 난수 공급원을 쓸 수 없습니다.')
  }
  const bytes = cryptoObj.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

/**
 * 세션 ID와 새로 고침 뒤 복원용 값을 sessionStorage에 두는 작은 저장소 래퍼.
 * 저장소가 막혀 있어도 던지지 않는다: 세션 ID는 메모리에 유지하고 복원은 하지 않는다.
 * @param {{ getStorage?: () => Storage, cryptoObj?: object }} [deps]
 */
export function createSession({ getStorage = () => globalThis.sessionStorage, cryptoObj } = {}) {
  let memorySessionId = null

  /** 저장소 접근은 어디서든 던질 수 있어 한곳에서 감싼다. */
  function withStorage(action, fallback) {
    try {
      return action(getStorage())
    } catch {
      return fallback
    }
  }

  function getSessionId() {
    if (memorySessionId) {
      return memorySessionId
    }
    const stored = withStorage((storage) => storage.getItem(SESSION_KEY), null)
    if (typeof stored === 'string' && UUID_PATTERN.test(stored)) {
      memorySessionId = stored
      return stored
    }
    memorySessionId = generateUuid(cryptoObj ?? globalThis.crypto)
    withStorage((storage) => storage.setItem(SESSION_KEY, memorySessionId))
    return memorySessionId
  }

  function saveRecommendations(response) {
    withStorage((storage) => storage.setItem(RECOMMENDATIONS_KEY, JSON.stringify(response)))
  }

  function loadRecommendations() {
    const raw = withStorage((storage) => storage.getItem(RECOMMENDATIONS_KEY), null)
    if (raw === null) {
      return null
    }
    try {
      const parsed = JSON.parse(raw)
      return isRecommendationResponse(parsed) ? parsed : null
    } catch {
      return null
    }
  }

  function clearRecommendations() {
    withStorage((storage) => storage.removeItem(RECOMMENDATIONS_KEY))
  }

  function saveLastPaidPassId(passId) {
    withStorage((storage) => storage.setItem(LAST_PAID_KEY, passId))
  }

  function loadLastPaidPassId() {
    const value = withStorage((storage) => storage.getItem(LAST_PAID_KEY), null)
    return typeof value === 'string' && value.length > 0 ? value : null
  }

  function clearLastPaidPassId() {
    withStorage((storage) => storage.removeItem(LAST_PAID_KEY))
  }

  return {
    getSessionId,
    saveRecommendations,
    loadRecommendations,
    clearRecommendations,
    saveLastPaidPassId,
    loadLastPaidPassId,
    clearLastPaidPassId,
  }
}

/** 앱이 쓰는 기본 세션 (실제 sessionStorage). */
export function useSession(deps) {
  return createSession(deps)
}

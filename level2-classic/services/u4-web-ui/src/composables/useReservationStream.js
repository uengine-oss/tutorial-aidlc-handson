import { buildEventsUrl } from '../api/api.js'
import { isReservation, isTerminalStatus } from '../lib/guards.js'

/** 복구 간격: 1초, 2초, 5초, 5초... (UR4.4) */
export const BACKOFF_MS = [1000, 2000, 5000]
/** 복구를 시도하는 총 시간 한도 */
export const RECOVERY_LIMIT_MS = 60000

const READY_STATE_CLOSED = 2

/**
 * 예약 한 건의 SSE 구독, 예약별 version 필터, 닫힌 연결 복구를 맡는다.
 *  - 예약당 구독은 하나: 새 구독 전에 기존 구독을 닫는다 (UR4.1).
 *  - 종료 상태의 스냅샷을 받으면 구독을 닫는다 (UR4.3).
 *  - 브라우저가 포기한 연결(CLOSED)은 단건 조회와 재구독으로 복구한다 (UR4.4).
 * 시계와 타이머는 주입할 수 있다.
 */
export function useReservationStream({
  api,
  getSessionId,
  EventSource: EventSourceCtor = globalThis.EventSource,
  now = () => Date.now(),
  setTimeoutFn = (...args) => globalThis.setTimeout(...args),
  clearTimeoutFn = (...args) => globalThis.clearTimeout(...args),
  warn = (...args) => console.warn(...args),
  onReservation,
  onGone,
  onSessionLost,
  onUnstable,
}) {
  /** 예약별로 이미 본 가장 높은 version */
  const versions = new Map()
  let eventSource = null
  let followedId = null
  /** 구독을 바꾸거나 멈출 때마다 올려, 늦게 도착한 비동기 결과를 버린다. */
  let generation = 0
  /** { startedAt, attempt, timer } | null. 연결이 열리거나 스냅샷이 오면 끝난다. */
  let recovery = null

  function accept(reservation) {
    const last = versions.get(reservation.reservationId)
    if (last !== undefined && reservation.version <= last) {
      return false
    }
    versions.set(reservation.reservationId, reservation.version)
    return true
  }

  function resetVersion(reservationId) {
    versions.delete(reservationId)
  }

  function closeSource() {
    if (eventSource) {
      eventSource.onopen = null
      eventSource.onerror = null
      eventSource.close()
      eventSource = null
    }
  }

  function clearRecovery() {
    if (recovery?.timer != null) {
      clearTimeoutFn(recovery.timer)
    }
    recovery = null
  }

  function stopAll() {
    generation += 1
    closeSource()
    clearRecovery()
    followedId = null
  }

  function handleSnapshot(event, source) {
    if (source !== eventSource) {
      return
    }
    let data
    try {
      data = JSON.parse(event.data)
    } catch {
      warn('예약 알림을 해석하지 못해 무시했습니다.')
      return
    }
    if (!isReservation(data) || data.reservationId !== followedId) {
      warn('형식이 맞지 않거나 다른 예약의 알림이라 무시했습니다.')
      return
    }
    clearRecovery()
    if (!accept(data)) {
      return
    }
    onReservation(data)
    if (isTerminalStatus(data.status)) {
      stopAll()
    }
  }

  function openSource() {
    const source = new EventSourceCtor(buildEventsUrl(followedId, getSessionId()))
    eventSource = source
    source.addEventListener('snapshot', (event) => handleSnapshot(event, source))
    source.onopen = () => {
      if (source === eventSource) {
        clearRecovery()
      }
    }
    source.onerror = () => {
      // CONNECTING이면 브라우저가 같은 URL로 다시 연결 중이다. CLOSED일 때만 우리가 복구한다.
      if (source === eventSource && source.readyState === READY_STATE_CLOSED) {
        startRecovery()
      }
    }
  }

  function startRecovery() {
    if (recovery?.timer != null) {
      return
    }
    if (!recovery) {
      recovery = { startedAt: now(), attempt: 0, timer: null }
    }
    scheduleAttempt()
  }

  function scheduleAttempt() {
    const delay = BACKOFF_MS[Math.min(recovery.attempt, BACKOFF_MS.length - 1)]
    const remaining = RECOVERY_LIMIT_MS - (now() - recovery.startedAt)
    const token = generation
    if (remaining <= delay) {
      recovery.timer = setTimeoutFn(() => giveUp(token), Math.max(0, remaining))
    } else {
      recovery.timer = setTimeoutFn(() => attemptRecovery(token), delay)
    }
  }

  function giveUp(token) {
    if (token !== generation) {
      return
    }
    stopAll()
    onUnstable()
  }

  async function attemptRecovery(token) {
    if (token !== generation || !recovery) {
      return
    }
    recovery.timer = null
    const id = followedId
    const result = await api.reservation(id)
    if (token !== generation) {
      return
    }

    if (result.outcome === 'response' && result.status === 404) {
      stopAll()
      onGone()
      return
    }
    if (result.outcome === 'response' && result.status === 401) {
      stopAll()
      onSessionLost()
      return
    }
    if (
      result.outcome === 'response' &&
      result.status === 200 &&
      isReservation(result.body) &&
      result.body.reservationId === id
    ) {
      if (accept(result.body)) {
        onReservation(result.body)
      }
      if (isTerminalStatus(result.body.status)) {
        stopAll()
        return
      }
      closeSource()
      openSource()
      recovery.attempt += 1
      return
    }

    recovery.attempt += 1
    scheduleAttempt()
  }

  /**
   * 예약을 구독한다. 기존 구독과 복구는 먼저 멈춘다.
   * reset: 이 예약의 version 필터를 비운다(새 예약을 따르기 시작할 때).
   */
  function follow(reservationId, { reset = true } = {}) {
    stopAll()
    followedId = reservationId
    if (reset) {
      resetVersion(reservationId)
    }
    openSource()
  }

  /** 구독과 복구를 멈춘다. version 필터는 유지한다. */
  function close() {
    stopAll()
  }

  /** 구독, 복구, version 필터를 모두 비운다 (새로 시작). */
  function reset() {
    stopAll()
    versions.clear()
  }

  return {
    follow,
    close,
    reset,
    accept,
    resetVersion,
    currentId: () => followedId,
  }
}

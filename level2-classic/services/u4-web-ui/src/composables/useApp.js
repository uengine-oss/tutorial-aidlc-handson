import { computed, ref } from 'vue'
import { createApi } from '../api/api.js'
import {
  MAX_ELAPSED_SECONDS,
  clampDelaySeconds,
  clampElapsedSeconds,
  DEFAULT_DELAY_SECONDS,
} from '../lib/format.js'
import {
  isPaymentAccepted,
  isRecommendationResponse,
  isReservation,
  isTerminalStatus,
  statusToView,
} from '../lib/guards.js'
import {
  DEFAULT_RESERVATION_MESSAGES,
  MSG_EMPTY,
  MSG_MISSING_DURATION,
  MSG_MISSING_PLACE,
  MSG_PASS_UNAVAILABLE,
  MSG_PAY_ERROR,
  MSG_PAYMENT_REJECTED,
  MSG_REASSIGN_HINT,
  MSG_RESERVATION_LOAD_FAILED,
  MSG_SEARCH_ERROR,
  MSG_SESSION_UNKNOWN,
  MSG_STREAM_UNSTABLE,
  recommendationCountMessage,
} from '../lib/messages.js'
import { createSession, generateUuid } from './useSession.js'
import { useReservationStream } from './useReservationStream.js'

const MAX_QUERY_LENGTH = 200
const SCENARIOS = ['APPROVE', 'DELAY', 'FAIL']

const INTERPRET_MESSAGES = {
  MISSING_PLACE: MSG_MISSING_PLACE,
  MISSING_DURATION: MSG_MISSING_DURATION,
}

/**
 * 화면 전체의 상태를 소유한다. 컴포넌트는 판단하지 않고 이 상태를 보여 주기만 한다.
 * 화면 상태는 resultView x reservationView의 곱이고 서로 다른 이벤트로만 바뀐다 (UR3.4).
 * 모든 바깥 의존(API, 저장소, EventSource, 시계, 타이머)은 주입할 수 있다.
 */
export function useApp(deps = {}) {
  const session = deps.session ?? createSession()
  const api = deps.api ?? createApi({ getSessionId: session.getSessionId })
  const now = deps.now ?? (() => Date.now())
  const uuid = deps.uuid ?? (() => generateUuid())
  const setIntervalFn = deps.setInterval ?? ((...args) => globalThis.setInterval(...args))
  const clearIntervalFn = deps.clearInterval ?? ((...args) => globalThis.clearInterval(...args))
  const target = deps.target ?? (typeof window === 'undefined' ? null : window)

  // ----- 상태 -----
  const text = ref('')
  const loading = ref(false)
  const paying = ref(false)
  const resultView = ref('idle')
  const reservationView = ref('none')
  const recommendations = ref([])
  const query = ref(null)
  const interpretMessage = ref('')
  const searchError = ref('')
  const payNotice = ref('')
  const reservationNotice = ref('')
  const reservation = ref(null)
  const elapsedSeconds = ref(0)
  const lastPaidPassId = ref(null)
  const announcement = ref('')
  const focusRequest = ref({ target: null, seq: 0 })
  const scenario = ref('APPROVE')
  const delaySeconds = ref(DEFAULT_DELAY_SECONDS)

  /** 응답을 받지 못한 마지막 결제 시도 { passId, durationHours, key } (UR3.2, F8) */
  let attempt = null
  let tickTimer = null

  // ----- 파생 값 -----
  const trimmedText = computed(() => text.value.trim())
  const canSubmit = computed(
    () =>
      !loading.value &&
      trimmedText.value.length >= 1 &&
      trimmedText.value.length <= MAX_QUERY_LENGTH,
  )
  const queryError = computed(() =>
    resultView.value === 'interpretError' ? interpretMessage.value : searchError.value,
  )
  const waitingConfirm = computed(
    () => reservationView.value === 'pending' && elapsedSeconds.value >= MAX_ELAPSED_SECONDS,
  )
  const payDisabled = computed(() => paying.value || reservationView.value === 'pending')
  const hasCards = computed(() => resultView.value === 'results')
  const failedPassId = computed(() =>
    reservationView.value === 'failed' &&
    lastPaidPassId.value !== null &&
    recommendations.value.some((item) => item.passId === lastPaidPassId.value)
      ? lastPaidPassId.value
      : null,
  )

  // ----- 작은 도우미 -----
  function announce(message) {
    announcement.value = message
  }

  function requestFocus(focusTarget) {
    focusRequest.value = { target: focusTarget, seq: focusRequest.value.seq + 1 }
  }

  function computeElapsed() {
    const requestedAt = Date.parse(reservation.value?.paymentRequestedAt ?? '')
    return clampElapsedSeconds((now() - requestedAt) / 1000)
  }

  function stopTick() {
    if (tickTimer !== null) {
      clearIntervalFn(tickTimer)
      tickTimer = null
    }
  }

  /** 결제 대기 동안만 1초 틱 하나를 둔다 (NFR1.3). */
  function syncTick() {
    if (reservationView.value === 'pending') {
      elapsedSeconds.value = computeElapsed()
      if (tickTimer === null) {
        tickTimer = setIntervalFn(() => {
          elapsedSeconds.value = computeElapsed()
        }, 1000)
      }
    } else {
      stopTick()
      elapsedSeconds.value = 0
    }
  }

  function setReservationView(view) {
    reservationView.value = view
    syncTick()
  }

  function clearReservationArea() {
    reservation.value = null
    setReservationView('none')
  }

  function reservationAnnouncement(view, message) {
    if (view === 'failed') {
      return `${message || MSG_PAYMENT_REJECTED} ${MSG_REASSIGN_HINT}`
    }
    return message || DEFAULT_RESERVATION_MESSAGES[view]
  }

  /**
   * 예약 스냅샷을 화면에 반영한다.
   * userDriven: 결제 응답이나 푸시처럼 사용자가 알아차려야 하는 변화(포커스 이동). 처음 열기 복원은 아니다.
   */
  function applyReservation(snapshot, { userDriven }) {
    const view = statusToView(snapshot.status)
    const changed =
      view !== reservationView.value || snapshot.reservationId !== reservation.value?.reservationId
    reservation.value = snapshot
    reservationNotice.value = ''
    setReservationView(view)
    announce(reservationAnnouncement(view, snapshot.message))
    if (userDriven && changed) {
      requestFocus('status')
    }
  }

  // ----- 구독 -----
  const stream = useReservationStream({
    api,
    getSessionId: session.getSessionId,
    EventSource: deps.EventSource ?? globalThis.EventSource,
    // 복구의 총 시간은 실제 시간(타이머와 같은 시계)으로 잰다. 경과 시간용 now()와 따로 둔다.
    now: deps.recoveryNow,
    onReservation: (snapshot) => applyReservation(snapshot, { userDriven: true }),
    onGone: () => clearReservationArea(),
    onSessionLost: () => {
      reservationNotice.value = MSG_SESSION_UNKNOWN
      announce(MSG_SESSION_UNKNOWN)
    },
    onUnstable: () => {
      reservationNotice.value = MSG_STREAM_UNSTABLE
      announce(MSG_STREAM_UNSTABLE)
    },
  })

  /**
   * 서버가 알려 준 예약 한 건을 따르기 시작한다. 결제 대기이면 구독하고 종료 상태이면 구독 없이 보인다.
   * 같은 version 필터를 거치며, 새 예약을 따르기 시작하므로 그 예약의 필터는 먼저 비운다.
   */
  function adoptReservation(snapshot, { userDriven }) {
    stream.close()
    stream.resetVersion(snapshot.reservationId)
    stream.accept(snapshot)
    applyReservation(snapshot, { userDriven })
    if (!isTerminalStatus(snapshot.status)) {
      stream.follow(snapshot.reservationId, { reset: false })
    }
  }

  /** 예약 한 건을 조회해 따른다. 실패는 안내하되 재시도하지 않는다. 404는 조용히 비운다. */
  async function loadAndAdopt(lookup) {
    const result = await lookup()
    if (result.outcome === 'response' && result.status === 404) {
      clearReservationArea()
      return false
    }
    if (result.outcome === 'response' && result.status === 200 && isReservation(result.body)) {
      adoptReservation(result.body, { userDriven: true })
      return true
    }
    reservationNotice.value = MSG_RESERVATION_LOAD_FAILED
    announce(MSG_RESERVATION_LOAD_FAILED)
    return false
  }

  // ----- 추천 (W2) -----
  function showResults(response) {
    query.value = response.query
    recommendations.value = response.recommendations
    if (response.recommendations.length > 0) {
      resultView.value = 'results'
      session.saveRecommendations(response)
      announce(recommendationCountMessage(response.recommendations.length))
      requestFocus('input')
    } else {
      resultView.value = 'empty'
      session.clearRecommendations()
      announce(MSG_EMPTY)
    }
  }

  function showInterpretError(message) {
    interpretMessage.value = message
    resultView.value = 'interpretError'
    recommendations.value = []
    query.value = null
    session.clearRecommendations()
    announce(message)
  }

  async function submitQuery() {
    if (!canSubmit.value) {
      return
    }
    loading.value = true
    searchError.value = ''
    try {
      const result = await api.recommend(trimmedText.value)
      if (
        result.outcome === 'response' &&
        result.status === 200 &&
        isRecommendationResponse(result.body)
      ) {
        showResults(result.body)
      } else if (
        result.outcome === 'response' &&
        result.status === 422 &&
        result.error &&
        Object.hasOwn(INTERPRET_MESSAGES, result.error.code)
      ) {
        showInterpretError(INTERPRET_MESSAGES[result.error.code])
      } else {
        searchError.value = MSG_SEARCH_ERROR
        announce(MSG_SEARCH_ERROR)
      }
    } finally {
      loading.value = false
    }
  }

  // ----- 결제 (W3) -----
  function attemptFor(passId, durationHours) {
    if (!attempt || attempt.passId !== passId || attempt.durationHours !== durationHours) {
      attempt = { passId, durationHours, key: uuid() }
    }
    return attempt
  }

  function recordPaid(passId) {
    lastPaidPassId.value = passId
    session.saveLastPaidPassId(passId)
  }

  function showPayNotice(message) {
    payNotice.value = message
    announce(message)
  }

  /** 결제 대기 예약을 서버 응답이 오기 전에 카드 정보로 먼저 그린다(주차장 이름·금액은 C2 응답에 없다). */
  function pendingPlaceholder(accepted, passId) {
    const card = recommendations.value.find((item) => item.passId === passId)
    return {
      reservationId: accepted.reservationId,
      version: 0,
      status: 'PENDING_PAYMENT',
      lotName: card?.lotName ?? '',
      durationHours: query.value.durationHours,
      amount: card?.price ?? 0,
      paymentRequestedAt: accepted.paymentRequestedAt,
      message: '',
    }
  }

  async function handlePaymentAccepted(accepted, passId) {
    attempt = null
    recordPaid(passId)
    if (accepted.status === 'PENDING_PAYMENT') {
      stream.close()
      stream.resetVersion(accepted.reservationId)
      applyReservation(pendingPlaceholder(accepted, passId), { userDriven: true })
      stream.follow(accepted.reservationId, { reset: false })
      return
    }
    await loadAndAdopt(() => api.reservation(accepted.reservationId))
  }

  async function handlePendingExists(error) {
    attempt = null
    await loadAndAdopt(() =>
      error?.reservationId ? api.reservation(error.reservationId) : api.currentReservation(),
    )
  }

  async function pay(passId) {
    if (paying.value || reservationView.value === 'pending' || !query.value) {
      return
    }
    const durationHours = query.value.durationHours
    const current = attemptFor(passId, durationHours)
    paying.value = true
    payNotice.value = ''
    try {
      const result = await api.pay({
        passId,
        durationHours,
        idempotencyKey: current.key,
        mockScenario: scenario.value,
        mockDelaySeconds: scenario.value === 'DELAY' ? clampDelaySeconds(delaySeconds.value) : 0,
      })

      if (result.outcome !== 'response') {
        // 네트워크 오류, 3초 초과: 응답을 받지 못했으므로 시도를 보관해 같은 키로 다시 보낸다.
        showPayNotice(MSG_PAY_ERROR)
      } else if (
        (result.status === 202 || result.status === 200) &&
        isPaymentAccepted(result.body)
      ) {
        await handlePaymentAccepted(result.body, passId)
      } else if (result.status === 409) {
        await handlePendingExists(result.error)
      } else if (result.status === 404 || result.status === 422) {
        attempt = null
        showPayNotice(MSG_PASS_UNAVAILABLE)
      } else if (result.status >= 500 || result.status === 200 || result.status === 202) {
        // 502와 그 밖의 5xx, 또는 해석할 수 없는 성공 응답: 결과를 알 수 없으므로 시도를 유지한다.
        showPayNotice(MSG_PAY_ERROR)
      } else {
        // 400, 401 등 확정된 거절: 시도를 지우고 접수 오류 안내를 보인다 (F8).
        attempt = null
        showPayNotice(MSG_PAY_ERROR)
      }
    } finally {
      paying.value = false
    }
  }

  // ----- 처음 열기 (W1) -----
  async function init() {
    const stored = session.loadRecommendations()
    if (stored) {
      query.value = stored.query
      recommendations.value = stored.recommendations
      resultView.value = stored.recommendations.length > 0 ? 'results' : 'idle'
    }
    lastPaidPassId.value = session.loadLastPaidPassId()

    const result = await api.currentReservation()
    if (result.outcome === 'response' && result.status === 200 && isReservation(result.body)) {
      adoptReservation(result.body, { userDriven: false })
    }
    requestFocus('input')
  }

  // ----- 새로 시작 (W5) -----
  function restart() {
    stream.reset()
    stopTick()
    attempt = null
    reservation.value = null
    reservationView.value = 'none'
    elapsedSeconds.value = 0
    resultView.value = 'idle'
    recommendations.value = []
    query.value = null
    text.value = ''
    interpretMessage.value = ''
    searchError.value = ''
    payNotice.value = ''
    reservationNotice.value = ''
    lastPaidPassId.value = null
    session.clearRecommendations()
    session.clearLastPaidPassId()
    requestFocus('input')
  }

  // ----- 시연 설정 (UR6.1) -----
  function setScenario(value) {
    scenario.value = SCENARIOS.includes(value) ? value : 'APPROVE'
  }

  function setDelaySeconds(value) {
    delaySeconds.value = clampDelaySeconds(value)
  }

  // ----- 정리 -----
  function onPageHide() {
    stream.close()
  }
  target?.addEventListener('pagehide', onPageHide)

  function dispose() {
    target?.removeEventListener('pagehide', onPageHide)
    stream.close()
    stopTick()
  }

  return {
    text,
    loading,
    paying,
    resultView,
    reservationView,
    recommendations,
    query,
    reservation,
    elapsedSeconds,
    lastPaidPassId,
    announcement,
    focusRequest,
    scenario,
    delaySeconds,
    payNotice,
    reservationNotice,
    canSubmit,
    queryError,
    waitingConfirm,
    payDisabled,
    hasCards,
    failedPassId,
    submitQuery,
    pay,
    restart,
    init,
    setScenario,
    setDelaySeconds,
    dispose,
  }
}

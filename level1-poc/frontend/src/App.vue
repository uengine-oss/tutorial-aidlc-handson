<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import * as api from './api.js'
import { watchReservation } from './events.js'
import RecommendationCards from './components/RecommendationCards.vue'
import PaymentPanel from './components/PaymentPanel.vue'
import ReservationDone from './components/ReservationDone.vue'

const text = ref('강남역 근처 2시간 주차권 찾아줘')
const step = ref('input') // input | recommend | payment | done | cancelled
const loading = ref(false)
const error = ref('')
const paymentError = ref(null) // 결제 서비스 표준 오류 { code, message }
const result = ref(null)
const ticket = ref(null)
const reservation = ref(null)
const payment = ref(null)
const remaining = ref(0)

let stopWatch = null
let countdownTimer = null
let paymentPoll = null

const recognitionSupported =
  typeof window !== 'undefined' && !!(window.SpeechRecognition || window.webkitSpeechRecognition)
const listening = ref(false)

function startVoice() {
  const Rec = window.SpeechRecognition || window.webkitSpeechRecognition
  const rec = new Rec()
  rec.lang = 'ko-KR'
  rec.onresult = (e) => {
    text.value = e.results[0][0].transcript
  }
  rec.onend = () => (listening.value = false)
  rec.onerror = () => {
    listening.value = false
    error.value = '음성 인식에 실패했어요. 글자로 입력해 주세요.'
  }
  listening.value = true
  rec.start()
}

async function guard(fn) {
  loading.value = true
  error.value = ''
  paymentError.value = null
  try {
    await fn()
  } catch (e) {
    const message = e.message || '알 수 없는 오류가 발생했어요.'
    // 결제 서비스 표준 오류({code, message})는 결제 화면 안에서 사유를 보여 준다.
    if (e.code && step.value === 'payment') {
      paymentError.value = { code: e.code, message }
    } else {
      error.value = message
    }
  } finally {
    loading.value = false
  }
}

function submit() {
  return guard(async () => {
    const r = await api.recommend(text.value)
    if (r.status === 'NEED_RETRY' || r.status === 'NO_RESULT') {
      result.value = null
      step.value = 'input'
      error.value = r.message
      return
    }
    result.value = r
    step.value = 'recommend'
  })
}

function cleanup() {
  if (stopWatch) stopWatch()
  clearInterval(countdownTimer)
  clearInterval(paymentPoll)
  stopWatch = countdownTimer = paymentPoll = null
}

function startCountdown(deadlineAt) {
  const tick = () => {
    remaining.value = Math.max(0, Math.ceil((new Date(deadlineAt).getTime() - Date.now()) / 1000))
  }
  tick()
  countdownTimer = setInterval(tick, 1000)
}

function onReservationStatus(r) {
  reservation.value = r
  if (r.status === 'CONFIRMED') {
    cleanup()
    step.value = 'done'
  } else if (r.status === 'CANCELLED') {
    cleanup()
    step.value = 'cancelled'
  }
}

function pollPayment() {
  clearInterval(paymentPoll)
  paymentPoll = setInterval(async () => {
    try {
      const p = await api.getPayment(payment.value.id)
      payment.value = p
      if (p.status !== 'PENDING') clearInterval(paymentPoll)
    } catch (e) {
      // 일시적 오류는 다음 주기에 다시 시도한다.
    }
  }, 1000)
}

function select(t) {
  return guard(async () => {
    cleanup()
    ticket.value = t
    reservation.value = await api.createReservation(t)
    payment.value = await api.createPayment(reservation.value.id, t.price)
    step.value = 'payment'
    startCountdown(reservation.value.deadlineAt)
    stopWatch = watchReservation(reservation.value.id, onReservationStatus, {
      fetchStatus: api.getReservation
    })
    pollPayment()
  })
}

const approve = () => guard(() => api.mockPgApprove(payment.value.id))
const fail = () => guard(() => api.mockPgFail(payment.value.id))

function retry() {
  return guard(async () => {
    payment.value = await api.createPayment(reservation.value.id, ticket.value.price)
    pollPayment()
  })
}

function restart() {
  cleanup()
  step.value = 'input'
  result.value = null
  ticket.value = reservation.value = payment.value = null
  error.value = ''
  paymentError.value = null
}

const showPayment = computed(() => step.value === 'payment' && payment.value && ticket.value)

onBeforeUnmount(cleanup)
</script>

<template>
  <main class="app">
    <h1>VoiceParking</h1>
    <p class="muted">말하듯이 입력하면 가장 알맞은 주차권을 골라 드려요.</p>

    <form class="search" @submit.prevent="submit">
      <input v-model="text" type="text" maxlength="200" placeholder="예: 강남역 근처 2시간 주차권 찾아줘"
             data-testid="voice-input" :disabled="loading || step === 'payment'" />
      <button v-if="recognitionSupported" type="button" data-testid="mic-button"
              :disabled="loading || listening" @click="startVoice">
        {{ listening ? '듣는 중…' : '말하기' }}
      </button>
      <button type="submit" class="primary" data-testid="submit-button"
              :disabled="loading || !text.trim() || step === 'payment'">찾기</button>
    </form>

    <p v-if="error" class="error" role="alert" data-testid="error-message">{{ error }}</p>

    <RecommendationCards v-if="step === 'recommend' && result" :result="result" @select="select" />

    <PaymentPanel v-if="showPayment" :ticket="ticket" :payment="payment"
                  :remaining-seconds="remaining" :busy="loading" :error="paymentError"
                  @approve="approve" @fail="fail" @retry="retry" />

    <ReservationDone v-if="step === 'done' && reservation" :reservation="reservation" @restart="restart" />

    <section v-if="step === 'cancelled'" class="error" data-testid="cancelled-message">
      <h2>예약이 취소되었어요</h2>
      <p>승인 마감 시간(3분) 안에 결제가 확인되지 않아 자동으로 취소했어요.</p>
      <button type="button" data-testid="restart-button" @click="restart">처음부터 다시</button>
    </section>
  </main>
</template>

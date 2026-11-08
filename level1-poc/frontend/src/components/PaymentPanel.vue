<script setup>
import { computed } from 'vue'

const props = defineProps({
  ticket: { type: Object, required: true },
  payment: { type: Object, required: true },
  remainingSeconds: { type: Number, default: 0 },
  busy: { type: Boolean, default: false },
  // 결제 서비스가 돌려준 표준 오류 { code, message } (없으면 null)
  error: { type: Object, default: null }
})
defineEmits(['approve', 'fail', 'retry'])

const countdown = computed(() => {
  const s = Math.max(0, props.remainingSeconds)
  const mm = String(Math.floor(s / 60)).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  return `${mm}:${ss}`
})
const failed = computed(() => props.payment.status === 'FAILED')
const lateIgnored = computed(() => props.payment.status === 'LATE_APPROVAL_IGNORED')
</script>

<template>
  <section class="payment" data-testid="payment-panel">
    <h2>결제 진행</h2>
    <p>{{ ticket.lotName }} · {{ ticket.price.toLocaleString('ko-KR') }}원</p>

    <p v-if="payment.status === 'PENDING'" class="waiting" data-testid="payment-waiting">
      결제 승인을 기다리는 중입니다. 남은 시간
      <strong data-testid="countdown">{{ countdown }}</strong>
    </p>

    <div v-if="failed" class="error" role="alert" data-testid="payment-error">
      결제에 실패했어요: {{ payment.failureReason }}
    </div>
    <div v-if="error" class="error" role="alert" data-testid="payment-api-error" :data-code="error.code">
      결제 처리 중 문제가 생겼어요: {{ error.message }}
    </div>
    <div v-if="lateIgnored" class="error" role="alert" data-testid="payment-late">
      승인이 마감 시간 뒤에 도착해 예약이 취소되었어요.
    </div>

    <div class="actions">
      <template v-if="payment.status === 'PENDING'">
        <button type="button" class="primary" data-testid="mock-approve-button"
                :disabled="busy" @click="$emit('approve')">모의 PG: 승인</button>
        <button type="button" data-testid="mock-fail-button"
                :disabled="busy" @click="$emit('fail')">모의 PG: 실패</button>
      </template>
      <button v-if="failed && payment.retryable" type="button" class="primary"
              data-testid="retry-payment-button" :disabled="busy" @click="$emit('retry')">
        다시 결제하기
      </button>
    </div>
  </section>
</template>

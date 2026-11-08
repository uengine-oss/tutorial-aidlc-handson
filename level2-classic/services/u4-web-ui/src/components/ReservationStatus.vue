<script setup>
import { computed, ref } from 'vue'
import { formatWon } from '../lib/format.js'
import {
  DEFAULT_RESERVATION_MESSAGES,
  MSG_PAYMENT_REJECTED,
  MSG_REASSIGN_HINT,
  MSG_WAITING_CONFIRM,
} from '../lib/messages.js'

const props = defineProps({
  state: { type: String, required: true },
  message: { type: String, default: '' },
  lotName: { type: String, default: '' },
  durationHours: { type: Number, default: 0 },
  amount: { type: Number, default: 0 },
  elapsedSeconds: { type: Number, default: 0 },
  /** 경과 180초에 닿았는데 서버가 아직 결과를 푸시하지 않음 */
  waitingConfirm: { type: Boolean, default: false },
  /** 같은 화면에 추천 카드가 있는가 (없으면 실패 상태에서 새로 검색하기를 보인다) */
  hasCards: { type: Boolean, default: false },
})
const emit = defineEmits(['restart'])

const headingElement = ref(null)

const headingText = computed(() => {
  switch (props.state) {
    case 'failed':
      return `(!) ${props.message || MSG_PAYMENT_REJECTED}`
    case 'cancelled':
      return `(!) ${props.message || DEFAULT_RESERVATION_MESSAGES.cancelled}`
    default:
      return props.message || DEFAULT_RESERVATION_MESSAGES[props.state]
  }
})
const restartLabel = computed(() => {
  if (props.state === 'cancelled') {
    return '다시 검색하기'
  }
  if (props.state === 'confirmed' || (props.state === 'failed' && !props.hasCards)) {
    return '새로 검색하기'
  }
  return ''
})

defineExpose({
  focusHeading: () => headingElement.value?.focus(),
})
</script>

<template>
  <section
    class="status"
    :class="`status--${state}`"
    :data-testid="`status-${state}`"
    aria-labelledby="status-heading"
  >
    <h2
      id="status-heading"
      ref="headingElement"
      class="status__heading"
      data-testid="status-heading"
      tabindex="-1"
    >
      {{ headingText }}
    </h2>

    <template v-if="state === 'pending'">
      <p class="status__summary">
        {{ lotName }} · {{ durationHours }}시간 · {{ formatWon(amount) }}원
      </p>
      <p class="status__elapsed">경과 {{ elapsedSeconds }}초 / 최대 180초</p>
      <p v-if="waitingConfirm" class="status__waiting">{{ MSG_WAITING_CONFIRM }}</p>
    </template>

    <template v-else-if="state === 'confirmed'">
      <p class="status__lot">{{ lotName }}</p>
      <p class="status__summary">
        이용 시간 {{ durationHours }}시간 · 결제 금액 {{ formatWon(amount) }}원
      </p>
    </template>

    <p v-else-if="state === 'failed'" class="status__hint">{{ MSG_REASSIGN_HINT }}</p>

    <button
      v-if="restartLabel"
      class="button"
      data-testid="restart"
      type="button"
      @click="emit('restart')"
    >
      {{ restartLabel }}
    </button>
  </section>
</template>

<script setup>
import { formatDistance, formatPriceLine, formatScore, rankLabel } from '../lib/format.js'

const props = defineProps({
  passId: { type: String, required: true },
  rank: { type: String, required: true },
  lotName: { type: String, required: true },
  /** 이용 시간 전체에 대한 합계 금액(원) */
  price: { type: Number, required: true },
  durationHours: { type: Number, required: true },
  distanceMeters: { type: Number, required: true },
  score: { type: Number, required: true },
  reasonText: { type: String, required: true },
  disabled: { type: Boolean, default: false },
  failed: { type: Boolean, default: false },
})
const emit = defineEmits(['select'])

function onSelect() {
  if (!props.disabled && !props.failed) {
    emit('select', props.passId)
  }
}
</script>

<template>
  <article
    class="card"
    :class="rank === 'BEST' ? 'card--best' : 'card--candidate'"
    :data-testid="`card-${passId}`"
  >
    <p class="card__rank">{{ rankLabel(rank) }}</p>
    <h3 class="card__name">{{ lotName }}</h3>
    <p class="card__score">{{ formatScore(score) }}</p>
    <p class="card__meta">
      {{ formatPriceLine(durationHours, price) }} · {{ formatDistance(distanceMeters) }}
    </p>
    <p class="card__reason"><span class="card__reason-label">이유:</span> {{ reasonText }}</p>
    <p v-if="failed" class="card__failed">(!) 결제 실패</p>
    <button
      class="button button--primary"
      type="button"
      :data-testid="`pay-${passId}`"
      :disabled="disabled || failed"
      @click="onSelect"
    >
      이 주차권 결제
    </button>
  </article>
</template>

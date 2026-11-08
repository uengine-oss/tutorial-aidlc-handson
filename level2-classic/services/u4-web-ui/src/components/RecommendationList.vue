<script setup>
import { computed } from 'vue'
import ParkingCard from './ParkingCard.vue'

const props = defineProps({
  /** 서버가 준 추천 (순서를 바꾸지 않는다) */
  items: { type: Array, required: true },
  durationHours: { type: Number, required: true },
  payDisabled: { type: Boolean, default: false },
  failedPassId: { type: String, default: null },
})
const emit = defineEmits(['select'])

// 최적 추천을 먼저, 비교 후보를 이어서 보인다. 각 묶음 안의 순서는 서버 순서 그대로다.
const best = computed(() => props.items.filter((item) => item.rank === 'BEST'))
const candidates = computed(() => props.items.filter((item) => item.rank !== 'BEST'))
</script>

<template>
  <section class="results" aria-label="추천 결과">
    <ParkingCard
      v-for="item in best"
      :key="item.passId"
      :pass-id="item.passId"
      :rank="item.rank"
      :lot-name="item.lotName"
      :price="item.price"
      :duration-hours="durationHours"
      :distance-meters="item.distanceMeters"
      :score="item.score"
      :reason-text="item.reason.text"
      :disabled="payDisabled"
      :failed="item.passId === failedPassId"
      @select="emit('select', $event)"
    />
    <div v-if="candidates.length > 0" class="results__candidates">
      <ParkingCard
        v-for="item in candidates"
        :key="item.passId"
        :pass-id="item.passId"
        :rank="item.rank"
        :lot-name="item.lotName"
        :price="item.price"
        :duration-hours="durationHours"
        :distance-meters="item.distanceMeters"
        :score="item.score"
        :reason-text="item.reason.text"
        :disabled="payDisabled"
        :failed="item.passId === failedPassId"
        @select="emit('select', $event)"
      />
    </div>
  </section>
</template>

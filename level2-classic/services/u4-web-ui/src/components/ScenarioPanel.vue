<script setup>
import { MAX_DELAY_SECONDS, MIN_DELAY_SECONDS, clampDelaySeconds } from '../lib/format.js'

// 개발용 시연 설정 (UR6.1). 실제 PG 연동 때는 이 영역을 제거한다.
defineProps({
  scenario: { type: String, default: 'APPROVE' },
  delaySeconds: { type: Number, default: 200 },
})
const emit = defineEmits(['update:scenario', 'update:delaySeconds'])

function onScenario(event) {
  emit('update:scenario', event.target.value)
}

function onDelay(event) {
  emit('update:delaySeconds', clampDelaySeconds(event.target.value))
}
</script>

<template>
  <details class="scenario">
    <summary>시연 설정</summary>
    <div class="scenario__row">
      <label for="scenario-select">모의 결제 결과</label>
      <select
        id="scenario-select"
        data-testid="scenario-select"
        :value="scenario"
        @change="onScenario"
      >
        <option value="APPROVE">승인</option>
        <option value="DELAY">지연</option>
        <option value="FAIL">실패</option>
      </select>
    </div>
    <div v-if="scenario === 'DELAY'" class="scenario__row">
      <label for="delay-input">지연 초</label>
      <input
        id="delay-input"
        data-testid="delay-input"
        type="number"
        step="1"
        :min="MIN_DELAY_SECONDS"
        :max="MAX_DELAY_SECONDS"
        :value="delaySeconds"
        @input="onDelay"
      />
    </div>
  </details>
</template>

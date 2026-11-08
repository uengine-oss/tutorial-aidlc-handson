<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import EmptyNotice from './components/EmptyNotice.vue'
import ErrorNotice from './components/ErrorNotice.vue'
import QueryInput from './components/QueryInput.vue'
import RecommendationList from './components/RecommendationList.vue'
import ReservationStatus from './components/ReservationStatus.vue'
import ScenarioPanel from './components/ScenarioPanel.vue'
import { useApp } from './composables/useApp.js'
import { MSG_EMPTY } from './lib/messages.js'

const props = defineProps({
  /** 시험이 가짜 API·저장소·EventSource를 넣는 자리. 실제 실행에서는 비워 둔다. */
  deps: { type: Object, default: () => ({}) },
})

// reactive로 감싸 템플릿에서 ref를 .value 없이 쓴다.
const app = reactive(useApp(props.deps))

const queryInput = ref(null)
const reservationStatus = ref(null)

// 포커스 이동은 useApp이 요청하고 화면이 수행한다:
// 사용자 동작으로 예약 상태가 바뀌었을 때만 상태 제목으로, 그 밖에는 입력창으로.
watch(
  () => app.focusRequest,
  async (request) => {
    await nextTick()
    if (request.target === 'status') {
      reservationStatus.value?.focusHeading()
    } else if (request.target === 'input') {
      queryInput.value?.focus()
    }
  },
)

onMounted(() => {
  app.init()
})
onBeforeUnmount(() => {
  app.dispose()
})
</script>

<template>
  <main class="app">
    <h1 class="app__title">VoiceParking</h1>

    <QueryInput
      ref="queryInput"
      v-model="app.text"
      :disabled="app.loading"
      :loading="app.loading"
      :error="app.queryError"
      @submit="app.submitQuery"
    />

    <ErrorNotice v-if="app.payNotice" data-testid="pay-error" :message="app.payNotice" />
    <ErrorNotice
      v-if="app.reservationNotice"
      data-testid="stream-notice"
      :message="app.reservationNotice"
    />

    <ReservationStatus
      v-if="app.reservationView !== 'none' && app.reservation"
      ref="reservationStatus"
      :state="app.reservationView"
      :message="app.reservation.message ?? ''"
      :lot-name="app.reservation.lotName"
      :duration-hours="app.reservation.durationHours"
      :amount="app.reservation.amount"
      :elapsed-seconds="app.elapsedSeconds"
      :waiting-confirm="app.waitingConfirm"
      :has-cards="app.hasCards"
      @restart="app.restart"
    />

    <RecommendationList
      v-if="app.resultView === 'results' && app.query"
      :items="app.recommendations"
      :duration-hours="app.query.durationHours"
      :pay-disabled="app.payDisabled"
      :failed-pass-id="app.failedPassId"
      @select="app.pay"
    />
    <EmptyNotice v-if="app.resultView === 'empty'" :message="MSG_EMPTY" />

    <ScenarioPanel
      :scenario="app.scenario"
      :delay-seconds="app.delaySeconds"
      @update:scenario="app.setScenario"
      @update:delay-seconds="app.setDelaySeconds"
    />

    <!-- 상태 변화를 알리는 영역. 1초마다 바뀌는 경과 시간은 이 영역 밖에 둔다. -->
    <div class="visually-hidden" aria-live="polite" aria-atomic="true" data-testid="live-region">
      {{ app.announcement }}
    </div>
  </main>
</template>

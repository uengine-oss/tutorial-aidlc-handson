<script setup>
defineProps({
  result: { type: Object, required: true }
})
defineEmits(['select'])

const won = (n) => `${n.toLocaleString('ko-KR')}원`
</script>

<template>
  <section class="recommendation" data-testid="recommendation">
    <p class="summary" data-testid="recommendation-summary">{{ result.message }}</p>
    <p v-if="result.notice" class="notice" data-testid="compare-notice">{{ result.notice }}</p>

    <div class="cards">
      <article
        v-for="c in result.candidates"
        :key="c.ticket.id"
        class="card"
        :class="{ best: c.best }"
        data-testid="ticket-card"
      >
        <span v-if="c.best" class="badge" data-testid="best-badge">최적 추천</span>
        <h3>{{ c.ticket.lotName }}</h3>
        <dl>
          <div><dt>가격</dt><dd>{{ won(c.ticket.price) }}</dd></div>
          <div><dt>거리</dt><dd>{{ c.ticket.distanceMeters }}m</dd></div>
          <div><dt>이용 시간</dt><dd>{{ c.ticket.durationHours }}시간</dd></div>
          <div><dt>종합 점수</dt><dd>{{ c.totalScore.toFixed(1) }}점</dd></div>
        </dl>
        <p v-if="c.best && c.reason" class="reason" data-testid="reason-text">{{ c.reason }}</p>
        <button
          type="button"
          class="primary"
          data-testid="select-ticket-button"
          @click="$emit('select', c.ticket)"
        >
          이 주차권으로 결제
        </button>
      </article>
    </div>
  </section>
</template>

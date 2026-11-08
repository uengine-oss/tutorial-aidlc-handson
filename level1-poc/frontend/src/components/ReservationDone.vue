<script setup>
defineProps({
  reservation: { type: Object, required: true }
})
defineEmits(['restart'])
</script>

<template>
  <section class="done" data-testid="reservation-done">
    <h2>예약이 완료되었어요</h2>
    <p data-testid="done-ticket">{{ reservation.ticketName }} · {{ reservation.price.toLocaleString('ko-KR') }}원</p>
    <p class="muted">예약 번호 {{ reservation.id }}</p>
    <button type="button" data-testid="restart-button" @click="$emit('restart')">새로 찾기</button>
  </section>
</template>

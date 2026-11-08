<script setup>
// (!) 문자와 문구로 오류를 알린다. 색만으로 전하지 않는다 (A4).
// role="alert"는 쓰지 않는다: 같은 문구를 App의 aria-live 영역이 알린다.
defineProps({
  message: { type: String, required: true },
})
</script>

<template>
  <p class="notice notice--error">(!) {{ message }}</p>
</template>

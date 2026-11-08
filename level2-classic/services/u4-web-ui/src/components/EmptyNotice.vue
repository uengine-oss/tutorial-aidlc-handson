<script setup>
defineProps({
  message: { type: String, required: true },
})
</script>

<template>
  <p class="notice notice--empty" data-testid="empty-notice">{{ message }}</p>
</template>

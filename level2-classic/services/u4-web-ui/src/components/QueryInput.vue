<script setup>
import { computed, ref } from 'vue'
import ErrorNotice from './ErrorNotice.vue'
import { MSG_LOADING, QUERY_EXAMPLE } from '../lib/messages.js'

const MAX_LENGTH = 200

const props = defineProps({
  modelValue: { type: String, default: '' },
  /** 요청 중이면 전송을 막는다 */
  disabled: { type: Boolean, default: false },
  /** 해석 오류·요청 오류 안내 */
  error: { type: String, default: '' },
  /** 요청 중이면 "찾는 중..."을 보인다 */
  loading: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'submit'])

const inputElement = ref(null)

const canSubmit = computed(() => {
  const length = props.modelValue.trim().length
  return !props.disabled && !props.loading && length >= 1 && length <= MAX_LENGTH
})
const describedBy = computed(() => (props.error ? 'query-hint query-error-text' : 'query-hint'))

function onInput(event) {
  emit('update:modelValue', event.target.value)
}

function onSubmit() {
  if (canSubmit.value) {
    emit('submit')
  }
}

defineExpose({
  focus: () => inputElement.value?.focus(),
})
</script>

<template>
  <form class="query" @submit.prevent="onSubmit">
    <label class="query__label" for="query-field">어디서 몇 시간 주차할지 말해 주세요</label>
    <div class="query__row">
      <input
        id="query-field"
        ref="inputElement"
        class="query__input"
        data-testid="query-input"
        type="text"
        autocomplete="off"
        :maxlength="MAX_LENGTH"
        :value="modelValue"
        :aria-describedby="describedBy"
        :aria-invalid="error ? 'true' : undefined"
        @input="onInput"
      />
      <button
        class="button button--primary"
        data-testid="query-submit"
        type="submit"
        :disabled="!canSubmit"
      >
        전송
      </button>
    </div>
    <p id="query-hint" class="query__hint">{{ QUERY_EXAMPLE }}</p>
    <p v-if="loading" class="query__loading">{{ MSG_LOADING }}</p>
    <ErrorNotice v-if="error" id="query-error-text" data-testid="query-error" :message="error" />
  </form>
</template>

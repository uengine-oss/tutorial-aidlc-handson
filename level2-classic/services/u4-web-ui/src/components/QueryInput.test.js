import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import QueryInput from './QueryInput.vue'

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

const build = (props = {}, options = {}) => {
  wrapper = mount(QueryInput, {
    props: { modelValue: '', disabled: false, loading: false, ...props },
    ...options,
  })
  return wrapper
}

describe('QueryInput', () => {
  it('입력창에 레이블이 연결되어 있다 (A5)', () => {
    build()
    const input = wrapper.get('[data-testid="query-input"]')
    const label = wrapper.get('label')
    expect(label.attributes('for')).toBe(input.attributes('id'))
    expect(label.text()).toBe('어디서 몇 시간 주차할지 말해 주세요')
  })

  it('최대 200자와 예시 문구를 갖는다', () => {
    build()
    expect(wrapper.get('[data-testid="query-input"]').attributes('maxlength')).toBe('200')
    expect(wrapper.text()).toContain('예: "강남역 근처 2시간 주차권 찾아줘"')
  })

  it('입력하면 update:modelValue를 낸다', async () => {
    build()
    await wrapper.get('[data-testid="query-input"]').setValue('강남역 근처 2시간')
    expect(wrapper.emitted('update:modelValue')).toEqual([['강남역 근처 2시간']])
  })

  it('입력창에 modelValue를 보인다', () => {
    build({ modelValue: '역삼역 3시간' })
    expect(wrapper.get('[data-testid="query-input"]').element.value).toBe('역삼역 3시간')
  })

  it.each(['', '   ', '\t\n'])('입력이 %j이면 전송 버튼이 비활성이다', (value) => {
    build({ modelValue: value })
    expect(wrapper.get('[data-testid="query-submit"]').attributes('disabled')).toBeDefined()
  })

  it('입력이 있으면 버튼이 활성이고 폼 제출(엔터)이 submit을 낸다', async () => {
    build({ modelValue: '강남역 2시간' })
    expect(wrapper.get('[data-testid="query-submit"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('버튼 클릭도 같은 submit을 낸다', async () => {
    build({ modelValue: '강남역 2시간' }, { attachTo: document.body })
    await wrapper.get('[data-testid="query-submit"]').trigger('click')
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('비어 있으면 폼이 제출되어도 submit을 내지 않는다', async () => {
    build({ modelValue: '  ' })
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('요청 중(loading)에는 "찾는 중..."을 보이고 전송을 막는다', async () => {
    build({ modelValue: '강남역 2시간', loading: true, disabled: true })
    expect(wrapper.text()).toContain('찾는 중...')
    expect(wrapper.get('[data-testid="query-submit"]').attributes('disabled')).toBeDefined()
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('loading이 아니면 "찾는 중..."을 보이지 않는다', () => {
    build({ modelValue: 'x' })
    expect(wrapper.text()).not.toContain('찾는 중...')
  })

  it('오류 문구가 있으면 (!)와 함께 보이고 입력창에 연결된다', () => {
    build({ modelValue: '2시간', error: '장소를 알려 주세요. 예: "강남역 근처 2시간"' })
    const error = wrapper.get('[data-testid="query-error"]')
    expect(error.text()).toContain('(!)')
    expect(error.text()).toContain('장소를 알려 주세요')
    const input = wrapper.get('[data-testid="query-input"]')
    expect(input.attributes('aria-describedby')).toContain(error.attributes('id'))
    expect(input.attributes('aria-invalid')).toBe('true')
  })

  it('오류가 없으면 오류 영역과 aria-invalid가 없다', () => {
    build({ modelValue: 'x' })
    expect(wrapper.find('[data-testid="query-error"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="query-input"]').attributes('aria-invalid')).toBeUndefined()
  })

  it('focus()를 노출해 입력창에 포커스를 옮긴다', () => {
    build({}, { attachTo: document.body })
    wrapper.vm.focus()
    expect(document.activeElement).toBe(wrapper.get('[data-testid="query-input"]').element)
  })
})

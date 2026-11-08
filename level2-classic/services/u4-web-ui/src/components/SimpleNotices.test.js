import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ErrorNotice from './ErrorNotice.vue'
import EmptyNotice from './EmptyNotice.vue'

describe('ErrorNotice', () => {
  it('(!) 문자와 문구를 함께 보인다 (색만으로 전하지 않는다)', () => {
    const wrapper = mount(ErrorNotice, { props: { message: '잠시 후 다시 시도해 주세요.' } })
    expect(wrapper.text()).toBe('(!) 잠시 후 다시 시도해 주세요.')
  })

  it('role="alert"를 쓰지 않는다 (알림은 App의 aria-live 영역이 맡는다)', () => {
    const wrapper = mount(ErrorNotice, { props: { message: 'x' } })
    expect(wrapper.attributes('role')).toBeUndefined()
  })

  it('부모가 준 data-testid를 루트에 받는다', () => {
    const wrapper = mount(ErrorNotice, {
      props: { message: 'x' },
      attrs: { 'data-testid': 'pay-error' },
    })
    expect(wrapper.attributes('data-testid')).toBe('pay-error')
  })

  it('메시지에 들어 있는 HTML은 글자로만 보인다', () => {
    const wrapper = mount(ErrorNotice, { props: { message: '<b>굵게</b><script>x()</script>' } })
    expect(wrapper.find('b').exists()).toBe(false)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.text()).toContain('<b>굵게</b>')
  })
})

describe('EmptyNotice', () => {
  it('후보 없음 안내를 보인다', () => {
    const wrapper = mount(EmptyNotice, {
      props: { message: '조건에 맞는 주차권이 없습니다. 장소나 시간을 바꿔 다시 말해 주세요.' },
    })
    expect(wrapper.get('[data-testid="empty-notice"]').text()).toBe(
      '조건에 맞는 주차권이 없습니다. 장소나 시간을 바꿔 다시 말해 주세요.',
    )
  })
})

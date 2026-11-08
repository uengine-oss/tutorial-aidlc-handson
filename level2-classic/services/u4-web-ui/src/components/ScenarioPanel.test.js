import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ScenarioPanel from './ScenarioPanel.vue'

const build = (props = {}) =>
  mount(ScenarioPanel, { props: { scenario: 'APPROVE', delaySeconds: 200, ...props } })

describe('ScenarioPanel', () => {
  it('접힌 <details> 영역이고 제목은 "시연 설정"이다', () => {
    const wrapper = build()
    const details = wrapper.get('details')
    expect(details.attributes('open')).toBeUndefined()
    expect(details.get('summary').text()).toBe('시연 설정')
  })

  it('승인·지연·실패 세 시나리오를 고를 수 있고 현재 값이 선택되어 있다', () => {
    const wrapper = build({ scenario: 'FAIL' })
    const select = wrapper.get('[data-testid="scenario-select"]')
    const values = select.findAll('option').map((option) => option.attributes('value'))
    expect(values).toEqual(['APPROVE', 'DELAY', 'FAIL'])
    expect(select.element.value).toBe('FAIL')
    expect(wrapper.get('label[for="scenario-select"]').text()).toBe('모의 결제 결과')
  })

  it('시나리오를 바꾸면 update:scenario를 낸다', async () => {
    const wrapper = build()
    await wrapper.get('[data-testid="scenario-select"]').setValue('DELAY')
    expect(wrapper.emitted('update:scenario')).toEqual([['DELAY']])
  })

  it('지연 초 입력은 지연 시나리오에서만 보인다', async () => {
    const wrapper = build({ scenario: 'APPROVE' })
    expect(wrapper.find('[data-testid="delay-input"]').exists()).toBe(false)
    await wrapper.setProps({ scenario: 'FAIL' })
    expect(wrapper.find('[data-testid="delay-input"]').exists()).toBe(false)
    await wrapper.setProps({ scenario: 'DELAY' })
    const input = wrapper.get('[data-testid="delay-input"]')
    expect(input.attributes('min')).toBe('1')
    expect(input.attributes('max')).toBe('600')
    expect(input.element.value).toBe('200')
  })

  it('지연 초를 입력하면 update:delaySeconds를 낸다', async () => {
    const wrapper = build({ scenario: 'DELAY' })
    await wrapper.get('[data-testid="delay-input"]').setValue('30')
    expect(wrapper.emitted('update:delaySeconds')).toEqual([[30]])
  })

  it.each([
    ['0', 1],
    ['-4', 1],
    ['601', 600],
    ['99999', 600],
    ['', 200],
  ])('범위 밖 지연 초 %j는 %i로 맞춰 낸다', async (typed, expected) => {
    const wrapper = build({ scenario: 'DELAY' })
    await wrapper.get('[data-testid="delay-input"]').setValue(typed)
    expect(wrapper.emitted('update:delaySeconds')).toEqual([[expected]])
  })
})

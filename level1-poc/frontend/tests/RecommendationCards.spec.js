import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RecommendationCards from '../src/components/RecommendationCards.vue'

const candidate = (id, best, reason) => ({
  ticket: { id, lotName: `주차장 ${id}`, price: 6000, distanceMeters: 250, durationHours: 2 },
  totalScore: 85,
  best,
  reason
})

const result = (candidates, notice = null) => ({
  status: 'OK',
  message: '강남역 2시간 주차권 추천 결과입니다.',
  candidates,
  notice
})

describe('RecommendationCards', () => {
  it('후보 카드 3개를 보여 주고 최적에만 강조와 이유를 붙인다', () => {
    const wrapper = mount(RecommendationCards, {
      props: {
        result: result([
          candidate('T003', true, '종합 점수 85.0점으로 1위입니다.'),
          candidate('T001', false, null),
          candidate('T002', false, null)
        ])
      }
    })

    expect(wrapper.findAll('[data-testid="ticket-card"]')).toHaveLength(3)
    expect(wrapper.findAll('[data-testid="best-badge"]')).toHaveLength(1)
    expect(wrapper.findAll('[data-testid="ticket-card"]')[0].classes()).toContain('best')
    expect(wrapper.findAll('[data-testid="reason-text"]')).toHaveLength(1)
    expect(wrapper.get('[data-testid="reason-text"]').text()).toContain('1위')
    expect(wrapper.find('[data-testid="compare-notice"]').exists()).toBe(false)
  })

  it('후보가 부족하면 안내 문구를 보여 준다', () => {
    const wrapper = mount(RecommendationCards, {
      props: { result: result([candidate('T101', true, '이유'), candidate('T102', false, null)],
                              '비교할 후보가 부족합니다. 찾은 2개만 보여 드려요.') }
    })
    expect(wrapper.findAll('[data-testid="ticket-card"]')).toHaveLength(2)
    expect(wrapper.get('[data-testid="compare-notice"]').text()).toContain('부족')
  })

  it('결제 버튼을 누르면 해당 주차권으로 select 이벤트를 보낸다', async () => {
    const wrapper = mount(RecommendationCards, {
      props: { result: result([candidate('T003', true, '이유'), candidate('T001', false, null)]) }
    })
    await wrapper.findAll('[data-testid="select-ticket-button"]')[1].trigger('click')
    expect(wrapper.emitted('select')[0][0].id).toBe('T001')
  })
})

import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RecommendationList from './RecommendationList.vue'
import { threeRecommendations } from '../../test/support/fakes.js'

const build = (props = {}) =>
  mount(RecommendationList, {
    props: {
      items: threeRecommendations(),
      durationHours: 2,
      payDisabled: false,
      failedPassId: null,
      ...props,
    },
  })

const cardIds = (wrapper) =>
  wrapper.findAll('[data-testid^="card-"]').map((node) => node.attributes('data-testid'))

describe('RecommendationList', () => {
  it('최적 1개와 비교 후보 2개를 서버가 준 순서대로 보인다', () => {
    const wrapper = build()
    expect(cardIds(wrapper)).toEqual(['card-p1', 'card-p2', 'card-p3'])
    expect(wrapper.get('[data-testid="card-p1"]').text()).toContain('최적 추천')
    expect(wrapper.get('[data-testid="card-p2"]').text()).toContain('비교 후보')
  })

  it('후보 비교 카드의 순서를 바꾸지 않는다 (점수가 낮아도 서버 순서)', () => {
    const items = threeRecommendations()
    const reordered = [items[0], items[2], items[1]]
    expect(cardIds(build({ items: reordered }))).toEqual(['card-p1', 'card-p3', 'card-p2'])
  })

  it('BEST가 목록 가운데 있어도 먼저 보인다', () => {
    const items = threeRecommendations()
    expect(cardIds(build({ items: [items[1], items[0], items[2]] }))).toEqual([
      'card-p1',
      'card-p2',
      'card-p3',
    ])
  })

  it('후보가 3개 미만이면 있는 카드만 보인다', () => {
    expect(cardIds(build({ items: threeRecommendations().slice(0, 2) }))).toEqual([
      'card-p1',
      'card-p2',
    ])
    expect(cardIds(build({ items: threeRecommendations().slice(0, 1) }))).toEqual(['card-p1'])
  })

  it('이용 시간 전체 합계 요금을 durationHours와 함께 보인다', () => {
    const wrapper = build({ durationHours: 3 })
    expect(wrapper.get('[data-testid="card-p2"]').text()).toContain('3시간 7,000원')
  })

  it('카드의 select를 passId와 함께 위로 전한다', async () => {
    const wrapper = build()
    await wrapper.get('[data-testid="pay-p3"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([['p3']])
  })

  it('payDisabled이면 모든 결제 버튼이 비활성이다', () => {
    const wrapper = build({ payDisabled: true })
    for (const id of ['p1', 'p2', 'p3']) {
      expect(wrapper.get(`[data-testid="pay-${id}"]`).attributes('disabled')).toBeDefined()
    }
  })

  it('failedPassId의 카드만 결제 실패로 표시한다', () => {
    const wrapper = build({ failedPassId: 'p2' })
    expect(wrapper.get('[data-testid="card-p2"]').text()).toContain('결제 실패')
    expect(wrapper.get('[data-testid="pay-p2"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="card-p1"]').text()).not.toContain('결제 실패')
    expect(wrapper.get('[data-testid="pay-p1"]').attributes('disabled')).toBeUndefined()
  })
})

import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ParkingCard from './ParkingCard.vue'

const baseProps = {
  passId: 'p1',
  rank: 'BEST',
  lotName: '강남역 공영주차장',
  price: 6000,
  durationHours: 2,
  distanceMeters: 120,
  score: 92,
  reasonText: '가격이 가장 큰 강점',
  disabled: false,
  failed: false,
}

const build = (props = {}) => mount(ParkingCard, { props: { ...baseProps, ...props } })

describe('ParkingCard', () => {
  it('최적 추천 카드의 모든 항목을 보인다 (UR2.3)', () => {
    const wrapper = build()
    const text = wrapper.get('[data-testid="card-p1"]').text()
    expect(text).toContain('최적 추천')
    expect(text).toContain('강남역 공영주차장')
    expect(text).toContain('2시간 6,000원')
    expect(text).toContain('120m')
    expect(text).toContain('종합 92점')
    expect(text).toContain('가격이 가장 큰 강점')
    expect(wrapper.get('[data-testid="pay-p1"]').text()).toBe('이 주차권 결제')
  })

  it('비교 후보 카드는 순위 문구와 변형 클래스가 다르다', () => {
    const wrapper = build({ passId: 'p2', rank: 'CANDIDATE' })
    expect(wrapper.get('[data-testid="card-p2"]').text()).toContain('비교 후보')
    expect(wrapper.get('[data-testid="card-p2"]').classes()).toContain('card--candidate')
    expect(build().get('[data-testid="card-p1"]').classes()).toContain('card--best')
  })

  it('버튼을 누르면 passId와 함께 select를 낸다', async () => {
    const wrapper = build({ passId: 'p7' })
    await wrapper.get('[data-testid="pay-p7"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([['p7']])
  })

  it('disabled이면 버튼이 비활성이고 눌러도 select를 내지 않는다', async () => {
    const wrapper = build({ disabled: true })
    const button = wrapper.get('[data-testid="pay-p1"]')
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('failed이면 "결제 실패"를 (!)와 함께 보이고 버튼이 비활성이다', () => {
    const wrapper = build({ failed: true })
    expect(wrapper.get('[data-testid="card-p1"]').text()).toContain('(!) 결제 실패')
    expect(wrapper.get('[data-testid="pay-p1"]').attributes('disabled')).toBeDefined()
  })

  it('실패하지 않은 카드에는 "결제 실패"가 없다', () => {
    expect(build().text()).not.toContain('결제 실패')
  })

  it('서버 문자열의 HTML은 요소가 되지 않고 글자로만 보인다', () => {
    const wrapper = build({
      lotName: '<script>alert(1)</script>',
      reasonText: '<img src=x onerror=alert(1)>',
    })
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<script>alert(1)</script>')
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
  })
})

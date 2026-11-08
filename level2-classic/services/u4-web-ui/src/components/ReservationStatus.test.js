import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ReservationStatus from './ReservationStatus.vue'

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

const build = (props = {}, options = {}) => {
  wrapper = mount(ReservationStatus, {
    props: {
      state: 'pending',
      message: '결제 승인을 기다리는 중입니다...',
      lotName: '강남역 공영주차장',
      durationHours: 2,
      amount: 6000,
      elapsedSeconds: 42,
      waitingConfirm: false,
      hasCards: true,
      ...props,
    },
    ...options,
  })
  return wrapper
}

describe('ReservationStatus - 결제 대기 (S5)', () => {
  it('서버 안내, 선택한 주차권 요약, 경과 시간을 보인다', () => {
    build()
    const root = wrapper.get('[data-testid="status-pending"]')
    expect(wrapper.get('[data-testid="status-heading"]').text()).toBe(
      '결제 승인을 기다리는 중입니다...',
    )
    expect(root.text()).toContain('강남역 공영주차장 · 2시간 · 6,000원')
    expect(root.text()).toContain('경과 42초 / 최대 180초')
    expect(wrapper.find('[data-testid="restart"]').exists()).toBe(false)
  })

  it('경과 시간이 바뀌면 다시 그린다', async () => {
    build({ elapsedSeconds: 1 })
    expect(wrapper.text()).toContain('경과 1초 / 최대 180초')
    await wrapper.setProps({ elapsedSeconds: 2 })
    expect(wrapper.text()).toContain('경과 2초 / 최대 180초')
  })

  it('180초에 닿으면 확인 중 문구를 보인다', () => {
    build({ elapsedSeconds: 180, waitingConfirm: true })
    expect(wrapper.text()).toContain('경과 180초 / 최대 180초')
    expect(wrapper.text()).toContain('승인 여부를 확인하는 중입니다...')
  })

  it('180초 전에는 확인 중 문구가 없다', () => {
    build({ elapsedSeconds: 179, waitingConfirm: false })
    expect(wrapper.text()).not.toContain('승인 여부를 확인하는 중입니다...')
  })

  it('서버 안내가 비어 있으면 기본 문구를 보인다', () => {
    build({ message: '' })
    expect(wrapper.get('[data-testid="status-heading"]').text()).toBe(
      '결제 승인을 기다리는 중입니다...',
    )
  })

  it('aria-live 영역이 아니다 (1초마다 바뀌는 경과 시간을 읽지 않게)', () => {
    build()
    expect(wrapper.element.closest('[aria-live]')).toBeNull()
    expect(wrapper.find('[aria-live]').exists()).toBe(false)
  })
})

describe('ReservationStatus - 예약 완료 (S6)', () => {
  it('주차장, 이용 시간, 결제 금액과 새로 검색하기를 보인다', async () => {
    build({ state: 'confirmed', message: '예약이 완료되었습니다' })
    const root = wrapper.get('[data-testid="status-confirmed"]')
    expect(wrapper.get('[data-testid="status-heading"]').text()).toBe('예약이 완료되었습니다')
    expect(root.text()).toContain('강남역 공영주차장')
    expect(root.text()).toContain('이용 시간 2시간 · 결제 금액 6,000원')
    expect(root.text()).not.toContain('경과')
    const restart = wrapper.get('[data-testid="restart"]')
    expect(restart.text()).toBe('새로 검색하기')
    await restart.trigger('click')
    expect(wrapper.emitted('restart')).toHaveLength(1)
  })
})

describe('ReservationStatus - 결제 실패 (S7)', () => {
  it('(!) 안내와 재선택 문구를 보이고 카드가 있으면 새로 검색하기는 없다', () => {
    build({ state: 'failed', message: '결제가 거절되었습니다', hasCards: true })
    const root = wrapper.get('[data-testid="status-failed"]')
    expect(root.text()).toContain('(!) 결제가 거절되었습니다')
    expect(root.text()).toContain('다른 주차권으로 다시 결제할 수 있습니다.')
    expect(wrapper.find('[data-testid="restart"]').exists()).toBe(false)
  })

  it('서버 안내가 비어 있어도 재선택 문구는 보인다', () => {
    build({ state: 'failed', message: '', hasCards: true })
    expect(wrapper.get('[data-testid="status-heading"]').text()).toBe('(!) 결제가 거절되었습니다.')
    expect(wrapper.text()).toContain('다른 주차권으로 다시 결제할 수 있습니다.')
  })

  it('추천 목록이 없으면 새로 검색하기를 보인다', async () => {
    build({ state: 'failed', message: '', hasCards: false })
    const restart = wrapper.get('[data-testid="restart"]')
    expect(restart.text()).toBe('새로 검색하기')
    await restart.trigger('click')
    expect(wrapper.emitted('restart')).toHaveLength(1)
  })
})

describe('ReservationStatus - 자동 취소 (S8)', () => {
  it('(!) 안내와 다시 검색하기를 보인다', async () => {
    build({ state: 'cancelled', message: '승인 지연으로 예약이 취소되었습니다.' })
    const root = wrapper.get('[data-testid="status-cancelled"]')
    expect(root.text()).toContain('(!) 승인 지연으로 예약이 취소되었습니다.')
    const restart = wrapper.get('[data-testid="restart"]')
    expect(restart.text()).toBe('다시 검색하기')
    await restart.trigger('click')
    expect(wrapper.emitted('restart')).toHaveLength(1)
  })
})

describe('ReservationStatus - 접근성과 안전', () => {
  it('제목은 tabindex -1이고 focusHeading()으로 포커스를 받는다', () => {
    build({}, { attachTo: document.body })
    const heading = wrapper.get('[data-testid="status-heading"]')
    expect(heading.attributes('tabindex')).toBe('-1')
    wrapper.vm.focusHeading()
    expect(document.activeElement).toBe(heading.element)
  })

  it('서버 문자열의 HTML은 글자로만 보인다', () => {
    build({
      message: '<img src=x onerror=alert(1)>',
      lotName: '<script>alert(2)</script>',
    })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.text()).toContain('<script>alert(2)</script>')
  })
})

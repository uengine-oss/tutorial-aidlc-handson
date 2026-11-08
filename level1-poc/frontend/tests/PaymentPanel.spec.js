import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PaymentPanel from '../src/components/PaymentPanel.vue'

const ticket = { id: 'T001', lotName: '강남역 공영주차장', price: 6000 }
const payment = (over) => ({ id: 'p1', status: 'PENDING', retryable: false, failureReason: null, ...over })

describe('PaymentPanel', () => {
  it('대기 중에는 카운트다운과 모의 PG 버튼을 보여 주고 재결제 버튼은 숨긴다', () => {
    const wrapper = mount(PaymentPanel, {
      props: { ticket, payment: payment(), remainingSeconds: 125 }
    })
    expect(wrapper.get('[data-testid="countdown"]').text()).toBe('02:05')
    expect(wrapper.find('[data-testid="mock-approve-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="retry-payment-button"]').exists()).toBe(false)
  })

  it('결제가 실패하면 사유와 재결제 버튼을 보여 주고 누르면 retry를 보낸다', async () => {
    const wrapper = mount(PaymentPanel, {
      props: {
        ticket,
        payment: payment({ status: 'FAILED', retryable: true, failureReason: '카드 한도 초과' }),
        remainingSeconds: 100
      }
    })
    expect(wrapper.get('[data-testid="payment-error"]').text()).toContain('카드 한도 초과')
    await wrapper.get('[data-testid="retry-payment-button"]').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('결제 서비스 표준 오류(code, message)가 오면 사유를 보여 준다', () => {
    const wrapper = mount(PaymentPanel, {
      props: {
        ticket,
        payment: payment({ status: 'FAILED', retryable: true, failureReason: '카드 한도 초과' }),
        remainingSeconds: 100,
        error: { code: 'RESERVATION_UNAVAILABLE', message: '예약 서비스 호출에 실패했습니다.' }
      }
    })
    const box = wrapper.get('[data-testid="payment-api-error"]')
    expect(box.text()).toContain('예약 서비스 호출에 실패했습니다.')
    expect(box.attributes('data-code')).toBe('RESERVATION_UNAVAILABLE')
    expect(wrapper.find('[data-testid="retry-payment-button"]').exists()).toBe(true)
  })

  it('표준 오류가 없으면 오류 상자를 숨긴다', () => {
    const wrapper = mount(PaymentPanel, { props: { ticket, payment: payment(), remainingSeconds: 10 } })
    expect(wrapper.find('[data-testid="payment-api-error"]').exists()).toBe(false)
  })

  it('남은 시간이 음수여도 00:00으로 보인다', () => {
    const wrapper = mount(PaymentPanel, {
      props: { ticket, payment: payment(), remainingSeconds: -5 }
    })
    expect(wrapper.get('[data-testid="countdown"]').text()).toBe('00:00')
  })

  it('늦은 승인으로 무시된 결제는 안내만 보이고 재결제 버튼은 없다', () => {
    const wrapper = mount(PaymentPanel, {
      props: { ticket, payment: payment({ status: 'LATE_APPROVAL_IGNORED' }), remainingSeconds: 0 }
    })
    expect(wrapper.find('[data-testid="payment-late"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="retry-payment-button"]').exists()).toBe(false)
  })
})

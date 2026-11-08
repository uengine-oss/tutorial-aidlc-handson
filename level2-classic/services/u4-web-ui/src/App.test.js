import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from './App.vue'
import { createApi } from './api/api.js'
import { createSession } from './composables/useSession.js'
import {
  FakeEventSource,
  createFakeFetch,
  createMemoryStorage,
  makeAccepted,
  makeQueryResponse,
  makeRecommendation,
  makeReservation,
  reply,
  threeRecommendations,
} from '../test/support/fakes.js'

const SEARCH = 'POST /api/search/recommendations'
const PAY = 'POST /api/payments'
const CURRENT = 'GET /api/reservations/current'

let wrapper
let fetchFn
let session

async function mountApp({ current, storedRecommendations, lastPaid } = {}) {
  FakeEventSource.reset()
  fetchFn = createFakeFetch()
  fetchFn.on(CURRENT, current ?? reply(404, { code: 'NO_RESERVATION', message: '예약 없음' }))
  const storage = createMemoryStorage()
  session = createSession({ getStorage: () => storage })
  if (storedRecommendations) {
    session.saveRecommendations(storedRecommendations)
  }
  if (lastPaid) {
    session.saveLastPaidPassId(lastPaid)
  }
  const api = createApi({ getSessionId: session.getSessionId, fetchFn })
  wrapper = mount(App, {
    props: { deps: { api, session, EventSource: FakeEventSource } },
    attachTo: document.body,
  })
  await flushPromises()
}

const byId = (id) => wrapper.get(`[data-testid="${id}"]`)
const maybe = (id) => wrapper.find(`[data-testid="${id}"]`)
const liveText = () => byId('live-region').text()

async function search(text = '강남역 근처 2시간 주차권 찾아줘') {
  await byId('query-input').setValue(text)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

async function pay(passId) {
  await byId(`pay-${passId}`).trigger('click')
  await flushPromises()
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('처음 열기', () => {
  it('현재 예약을 조회하고 입력창에 포커스를 둔다', async () => {
    await mountApp()
    expect(fetchFn.callsFor(CURRENT)).toHaveLength(1)
    expect(document.activeElement).toBe(byId('query-input').element)
    expect(maybe('status-heading').exists()).toBe(false)
    expect(maybe('empty-notice').exists()).toBe(false)
    expect(wrapper.text()).toContain('VoiceParking')
  })

  it('모든 상호작용 요소에 data-testid가 있다', async () => {
    await mountApp()
    for (const id of ['query-input', 'query-submit', 'scenario-select', 'live-region']) {
      expect(maybe(id).exists(), id).toBe(true)
    }
  })

  it('결제 대기 예약을 복원하면 S5를 보이되 포커스는 입력창에 둔다', async () => {
    await mountApp({ current: reply(200, makeReservation({ version: 2 })) })
    expect(maybe('status-pending').exists()).toBe(true)
    expect(document.activeElement).toBe(byId('query-input').element)
    expect(FakeEventSource.open).toHaveLength(1)
  })

  it('저장된 추천 목록을 복원하고 실패 예약이면 해당 카드를 실패로 보인다', async () => {
    await mountApp({
      current: reply(
        200,
        makeReservation({ version: 3, status: 'PAYMENT_FAILED', message: '거절' }),
      ),
      storedRecommendations: makeQueryResponse(threeRecommendations()),
      lastPaid: 'p2',
    })
    expect(maybe('card-p1').exists()).toBe(true)
    expect(byId('card-p2').text()).toContain('결제 실패')
    expect(byId('pay-p2').attributes('disabled')).toBeDefined()
    expect(byId('pay-p1').attributes('disabled')).toBeUndefined()
    expect(maybe('restart').exists()).toBe(false)
  })

  it('실패 예약인데 추천 목록이 없으면 "새로 검색하기"를 보인다', async () => {
    await mountApp({
      current: reply(200, makeReservation({ version: 3, status: 'PAYMENT_FAILED' })),
    })
    expect(byId('restart').text()).toBe('새로 검색하기')
    await byId('restart').trigger('click')
    await flushPromises()
    expect(maybe('status-failed').exists()).toBe(false)
    expect(document.activeElement).toBe(byId('query-input').element)
  })
})

describe('추천 (S2~S4)', () => {
  it('후보가 있으면 카드를 서버 순서로 보이고 "추천 3건"을 알린다', async () => {
    await mountApp()
    fetchFn.once(SEARCH, reply(200, makeQueryResponse(threeRecommendations())))
    await search()
    const ids = wrapper
      .findAll('[data-testid^="card-"]')
      .map((node) => node.attributes('data-testid'))
    expect(ids).toEqual(['card-p1', 'card-p2', 'card-p3'])
    expect(liveText()).toBe('추천 3건')
    expect(document.activeElement).toBe(byId('query-input').element)
  })

  it('해석 오류는 (!) 안내를 입력 아래에 보이고 알린다', async () => {
    await mountApp()
    fetchFn.once(SEARCH, reply(422, { code: 'MISSING_PLACE', message: 'x' }))
    await search('2시간 주차권')
    expect(byId('query-error').text()).toContain('(!) 장소를 알려 주세요. 예: "강남역 근처 2시간"')
    expect(liveText()).toContain('장소를 알려 주세요')
    expect(maybe('card-p1').exists()).toBe(false)
  })

  it('후보가 없으면 S4 안내를 보이고 알린다', async () => {
    await mountApp()
    fetchFn.once(SEARCH, reply(200, makeQueryResponse([])))
    await search()
    expect(byId('empty-notice').text()).toBe(
      '조건에 맞는 주차권이 없습니다. 장소나 시간을 바꿔 다시 말해 주세요.',
    )
    expect(liveText()).toBe('조건에 맞는 주차권이 없습니다. 장소나 시간을 바꿔 다시 말해 주세요.')
  })

  it('요청 오류는 안내를 보이고 이전 결과를 유지한다', async () => {
    await mountApp()
    fetchFn.once(SEARCH, reply(200, makeQueryResponse(threeRecommendations())))
    await search()
    fetchFn.once(SEARCH, reply(500, undefined))
    await search('다시')
    expect(byId('query-error').text()).toContain('(!) 잠시 후 다시 시도해 주세요.')
    expect(maybe('card-p1').exists()).toBe(true)
  })

  it('공백만 입력하면 전송 버튼이 비활성이다', async () => {
    await mountApp()
    await byId('query-input').setValue('   ')
    expect(byId('query-submit').attributes('disabled')).toBeDefined()
  })
})

describe('결제와 실시간 갱신 (S5~S8)', () => {
  async function startPayment(passId = 'p1') {
    fetchFn.once(SEARCH, reply(200, makeQueryResponse(threeRecommendations())))
    await search()
    fetchFn.once(PAY, reply(202, makeAccepted({ paymentRequestedAt: new Date().toISOString() })))
    await pay(passId)
  }

  it('결제하면 대기 상태가 되고 상태 제목으로 포커스가 옮겨가며 결제 버튼이 막힌다', async () => {
    await mountApp()
    await startPayment()
    expect(maybe('status-pending').exists()).toBe(true)
    expect(byId('status-heading').text()).toBe('결제 승인을 기다리는 중입니다...')
    expect(document.activeElement).toBe(byId('status-heading').element)
    for (const id of ['p1', 'p2', 'p3']) {
      expect(byId(`pay-${id}`).attributes('disabled')).toBeDefined()
    }
    expect(liveText()).toBe('결제 승인을 기다리는 중입니다...')
  })

  it('경과 시간은 aria-live 영역 밖에 있다', async () => {
    await mountApp()
    await startPayment()
    expect(byId('status-pending').text()).toContain('경과')
    expect(byId('live-region').text()).not.toContain('경과')
    expect(byId('live-region').element.contains(byId('status-pending').element)).toBe(false)
    expect(byId('status-pending').element.closest('[aria-live]')).toBeNull()
    expect(byId('live-region').attributes('aria-live')).toBe('polite')
  })

  it('승인되면 예약 완료를 보이고 알리며, 새로 검색하기는 입력창으로 포커스를 돌린다', async () => {
    await mountApp()
    await startPayment()
    FakeEventSource.open[0].snapshot(
      makeReservation({ version: 2, status: 'CONFIRMED', message: '예약이 완료되었습니다' }),
    )
    await flushPromises()
    expect(maybe('status-confirmed').exists()).toBe(true)
    expect(byId('status-confirmed').text()).toContain('이용 시간 2시간 · 결제 금액 6,000원')
    expect(liveText()).toBe('예약이 완료되었습니다')
    expect(document.activeElement).toBe(byId('status-heading').element)

    await byId('restart').trigger('click')
    await flushPromises()
    expect(maybe('status-confirmed').exists()).toBe(false)
    expect(maybe('card-p1').exists()).toBe(false)
    expect(byId('query-input').element.value).toBe('')
    expect(document.activeElement).toBe(byId('query-input').element)
  })

  it('결제 실패이면 실패 카드는 막히고 다른 후보로 다시 결제할 수 있다', async () => {
    await mountApp()
    await startPayment('p1')
    FakeEventSource.open[0].snapshot(
      makeReservation({ version: 2, status: 'PAYMENT_FAILED', message: '결제가 거절되었습니다' }),
    )
    await flushPromises()
    expect(byId('status-failed').text()).toContain('(!) 결제가 거절되었습니다')
    expect(byId('card-p1').text()).toContain('결제 실패')
    expect(byId('pay-p1').attributes('disabled')).toBeDefined()
    expect(byId('pay-p2').attributes('disabled')).toBeUndefined()
    expect(maybe('restart').exists()).toBe(false)

    fetchFn.once(
      PAY,
      reply(
        202,
        makeAccepted({ reservationId: 'r2', paymentRequestedAt: new Date().toISOString() }),
      ),
    )
    await pay('p2')
    expect(maybe('status-pending').exists()).toBe(true)
    expect(FakeEventSource.open[0].url).toContain('/r2/events')
  })

  it('자동 취소이면 (!) 안내와 다시 검색하기를 보인다', async () => {
    await mountApp()
    await startPayment()
    FakeEventSource.open[0].snapshot(
      makeReservation({
        version: 2,
        status: 'CANCELLED',
        message: '승인 지연으로 예약이 취소되었습니다.',
      }),
    )
    await flushPromises()
    expect(byId('status-cancelled').text()).toContain('(!) 승인 지연으로 예약이 취소되었습니다.')
    expect(byId('restart').text()).toBe('다시 검색하기')
  })

  it('결제 접수 오류는 결과 영역 위에 보이고 알린다', async () => {
    await mountApp()
    fetchFn.once(SEARCH, reply(200, makeQueryResponse(threeRecommendations())))
    await search()
    fetchFn.once(PAY, reply(502, { code: 'UPSTREAM_FAILED', message: 'x' }))
    await pay('p1')
    expect(byId('pay-error').text()).toBe('(!) 결제 요청을 접수하지 못했습니다. 다시 눌러 주세요.')
    expect(liveText()).toBe('결제 요청을 접수하지 못했습니다. 다시 눌러 주세요.')
    expect(maybe('status-pending').exists()).toBe(false)
    expect(byId('pay-p1').attributes('disabled')).toBeUndefined()
  })

  it('결제 대기 중에도 새 검색은 되고 결제 버튼은 계속 막힌다', async () => {
    await mountApp()
    await startPayment()
    fetchFn.once(SEARCH, reply(200, makeQueryResponse(threeRecommendations().slice(0, 2))))
    await search('역삼역 2시간')
    expect(maybe('card-p3').exists()).toBe(false)
    expect(maybe('status-pending').exists()).toBe(true)
    expect(byId('pay-p1').attributes('disabled')).toBeDefined()
  })

  it('시연 설정의 값을 결제 요청에 싣는다', async () => {
    await mountApp()
    fetchFn.once(SEARCH, reply(200, makeQueryResponse(threeRecommendations())))
    await search()
    await byId('scenario-select').setValue('DELAY')
    await byId('delay-input').setValue('30')
    fetchFn.once(PAY, reply(502, undefined))
    await pay('p1')
    expect(fetchFn.bodyOf(fetchFn.callsFor(PAY)[0])).toMatchObject({
      mockScenario: 'DELAY',
      mockDelaySeconds: 30,
    })
  })

  it('pagehide에서 구독을 닫는다', async () => {
    await mountApp()
    await startPayment()
    window.dispatchEvent(new Event('pagehide'))
    expect(FakeEventSource.open).toHaveLength(0)
  })

  it('화면을 닫으면(unmount) 구독을 닫는다', async () => {
    await mountApp()
    await startPayment()
    wrapper.unmount()
    wrapper = null
    expect(FakeEventSource.open).toHaveLength(0)
  })
})

describe('서버 문자열은 글자로만 보인다 (NFR6.1)', () => {
  it('추천 카드와 예약 안내의 HTML 조각이 요소가 되지 않는다', async () => {
    await mountApp()
    const hostile = [
      makeRecommendation({
        lotName: '<script>window.__xss = 1</script>',
        reason: { topFactor: 'PRICE', text: '<img src=x onerror="window.__xss = 2">' },
      }),
    ]
    fetchFn.once(SEARCH, reply(200, makeQueryResponse(hostile)))
    await search()
    fetchFn.once(PAY, reply(202, makeAccepted({ paymentRequestedAt: new Date().toISOString() })))
    await pay('p1')
    FakeEventSource.open[0].snapshot(
      makeReservation({
        version: 2,
        status: 'CONFIRMED',
        lotName: '<b>굵게</b>',
        message: '<iframe src="javascript:alert(1)"></iframe>',
      }),
    )
    await flushPromises()

    const root = wrapper.element
    expect(root.querySelector('script')).toBeNull()
    expect(root.querySelector('img')).toBeNull()
    expect(root.querySelector('iframe')).toBeNull()
    expect(root.querySelector('b')).toBeNull()
    expect(window.__xss).toBeUndefined()
    expect(wrapper.text()).toContain('<b>굵게</b>')
    expect(byId('status-heading').text()).toBe('<iframe src="javascript:alert(1)"></iframe>')
    expect(liveText()).toBe('<iframe src="javascript:alert(1)"></iframe>')
  })
})

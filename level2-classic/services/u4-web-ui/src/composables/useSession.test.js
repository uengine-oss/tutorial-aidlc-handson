import { describe, expect, it } from 'vitest'
import { createSession, generateUuid } from './useSession.js'
import {
  createBrokenStorage,
  createMemoryStorage,
  makeQueryResponse,
  threeRecommendations,
} from '../../test/support/fakes.js'

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

describe('generateUuid', () => {
  it('crypto.randomUUID가 있으면 그것을 쓴다', () => {
    const value = generateUuid({ randomUUID: () => 'fixed-uuid' })
    expect(value).toBe('fixed-uuid')
  })

  it('randomUUID가 없으면 getRandomValues로 UUID v4를 만든다', () => {
    const fill = (bytes) => {
      bytes.fill(0)
      return bytes
    }
    expect(generateUuid({ getRandomValues: fill })).toBe('00000000-0000-4000-8000-000000000000')
    const random = generateUuid({
      getRandomValues: (bytes) => globalThis.crypto.getRandomValues(bytes),
    })
    expect(random).toMatch(UUID_V4)
  })

  it('안전한 난수 공급원이 없으면 던진다', () => {
    expect(() => generateUuid({})).toThrow()
    expect(() => generateUuid(null)).toThrow()
  })
})

describe('createSession - 세션 ID', () => {
  it('저장소에 없으면 UUID를 만들어 저장하고 같은 값을 계속 돌려준다', () => {
    const storage = createMemoryStorage()
    const session = createSession({ getStorage: () => storage })
    const first = session.getSessionId()
    expect(first).toMatch(UUID_V4)
    expect(session.getSessionId()).toBe(first)
    expect(Object.values(storage.snapshot())).toContain(first)
  })

  it('새로 고침(새 인스턴스) 뒤에도 같은 탭의 저장소에서 같은 ID를 읽는다', () => {
    const storage = createMemoryStorage()
    const first = createSession({ getStorage: () => storage }).getSessionId()
    const second = createSession({ getStorage: () => storage }).getSessionId()
    expect(second).toBe(first)
  })

  it('저장된 값이 UUID 형식이 아니면 새로 만든다', () => {
    const storage = createMemoryStorage()
    storage.setItem('vp.sessionId', 'not-a-uuid')
    const id = createSession({ getStorage: () => storage }).getSessionId()
    expect(id).toMatch(UUID_V4)
    expect(id).not.toBe('not-a-uuid')
  })

  it('저장소를 쓸 수 없으면 만든 ID를 메모리에 유지해 호출마다 새로 만들지 않는다', () => {
    const session = createSession({ getStorage: () => createBrokenStorage() })
    const first = session.getSessionId()
    expect(first).toMatch(UUID_V4)
    expect(session.getSessionId()).toBe(first)
  })

  it('저장소 접근 자체가 던져도(getStorage가 던짐) 메모리로 계속한다', () => {
    const session = createSession({
      getStorage: () => {
        throw new Error('SecurityError')
      },
    })
    const first = session.getSessionId()
    expect(session.getSessionId()).toBe(first)
  })
})

describe('createSession - 추천 목록 저장', () => {
  it('저장한 추천 응답을 복원한다', () => {
    const storage = createMemoryStorage()
    const session = createSession({ getStorage: () => storage })
    const response = makeQueryResponse(threeRecommendations())
    session.saveRecommendations(response)
    expect(createSession({ getStorage: () => storage }).loadRecommendations()).toEqual(response)
  })

  it('저장된 것이 없으면 null이다', () => {
    const session = createSession({ getStorage: () => createMemoryStorage() })
    expect(session.loadRecommendations()).toBeNull()
  })

  it('지우면 복원되지 않는다', () => {
    const storage = createMemoryStorage()
    const session = createSession({ getStorage: () => storage })
    session.saveRecommendations(makeQueryResponse(threeRecommendations()))
    session.clearRecommendations()
    expect(session.loadRecommendations()).toBeNull()
  })

  it('깨진 JSON이나 형식이 틀린 값은 버리고 null이다', () => {
    const storage = createMemoryStorage()
    const session = createSession({ getStorage: () => storage })
    storage.setItem('vp.recommendations', '{broken')
    expect(session.loadRecommendations()).toBeNull()
    storage.setItem('vp.recommendations', JSON.stringify({ query: {}, recommendations: 'x' }))
    expect(session.loadRecommendations()).toBeNull()
  })

  it('저장소를 쓸 수 없어도 던지지 않고 복원 없이 동작한다', () => {
    const session = createSession({ getStorage: () => createBrokenStorage() })
    expect(() => session.saveRecommendations(makeQueryResponse([]))).not.toThrow()
    expect(session.loadRecommendations()).toBeNull()
    expect(() => session.clearRecommendations()).not.toThrow()
  })
})

describe('createSession - 마지막 결제 passId', () => {
  it('저장하고 복원하고 지운다', () => {
    const storage = createMemoryStorage()
    const session = createSession({ getStorage: () => storage })
    expect(session.loadLastPaidPassId()).toBeNull()
    session.saveLastPaidPassId('p2')
    expect(createSession({ getStorage: () => storage }).loadLastPaidPassId()).toBe('p2')
    session.clearLastPaidPassId()
    expect(session.loadLastPaidPassId()).toBeNull()
  })

  it('저장소를 쓸 수 없으면 null이고 던지지 않는다', () => {
    const session = createSession({ getStorage: () => createBrokenStorage() })
    expect(() => session.saveLastPaidPassId('p1')).not.toThrow()
    expect(session.loadLastPaidPassId()).toBeNull()
    expect(() => session.clearLastPaidPassId()).not.toThrow()
  })
})

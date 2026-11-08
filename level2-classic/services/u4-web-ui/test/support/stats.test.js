import { describe, expect, it, vi } from 'vitest'
import { dropWarmup, judge, percentile, summarize } from './stats.js'

describe('판정 통계 도구', () => {
  const hundred = Array.from({ length: 100 }, (_, i) => i + 1)

  it('가까운 순위 방식으로 백분위수를 구한다', () => {
    expect(percentile(hundred, 0.5)).toBe(50)
    expect(percentile(hundred, 0.99)).toBe(99)
    expect(percentile(hundred, 1)).toBe(100)
    expect(percentile([7], 0.99)).toBe(7)
  })

  it('입력 순서와 관계없이 정렬해서 계산한다', () => {
    expect(percentile([5, 1, 3], 0.5)).toBe(3)
  })

  it('표본이 없으면 던진다', () => {
    expect(() => percentile([], 0.5)).toThrow()
  })

  it('요약은 중앙값, p99, 최댓값을 담는다', () => {
    expect(summarize(hundred)).toEqual({ count: 100, median: 50, p99: 99, max: 100 })
  })

  it('judge는 요약과 한도를 돌려주고 중앙값·p99·최댓값을 출력한다', () => {
    const info = vi.spyOn(console, 'info').mockImplementation(() => {})
    const result = judge('시험', hundred, 99, 2)
    expect(result).toMatchObject({ p99: 99, max: 100, limitMs: 99, maxLimit: 198 })
    expect(info).toHaveBeenCalledTimes(1)
    expect(info.mock.calls[0][0]).toMatch(/시험.*중앙값=50ms.*p99=99ms.*최댓값=100ms/)
    info.mockRestore()
  })

  it('예열 구간을 버린다', () => {
    expect(dropWarmup(Array.from({ length: 120 }, (_, i) => i))).toHaveLength(100)
    expect(dropWarmup([1, 2, 3, 4], 2)).toEqual([3, 4])
  })
})

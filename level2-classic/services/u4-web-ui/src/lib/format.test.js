import { describe, expect, it } from 'vitest'
import {
  clampDelaySeconds,
  clampElapsedSeconds,
  formatDistance,
  formatPriceLine,
  formatScore,
  formatWon,
  rankLabel,
} from './format.js'

describe('formatWon', () => {
  it('천 단위 구분 기호를 넣는다', () => {
    expect(formatWon(0)).toBe('0')
    expect(formatWon(500)).toBe('500')
    expect(formatWon(6000)).toBe('6,000')
    expect(formatWon(1234567)).toBe('1,234,567')
  })
})

describe('formatPriceLine', () => {
  it('"N시간 N원" 형태로 만든다', () => {
    expect(formatPriceLine(2, 6000)).toBe('2시간 6,000원')
    expect(formatPriceLine(1, 1500)).toBe('1시간 1,500원')
  })
})

describe('거리·점수·순위 표시', () => {
  it('거리와 점수 문구를 만든다', () => {
    expect(formatDistance(120)).toBe('120m')
    expect(formatScore(92)).toBe('종합 92점')
  })

  it('순위 문구를 만든다', () => {
    expect(rankLabel('BEST')).toBe('최적 추천')
    expect(rankLabel('CANDIDATE')).toBe('비교 후보')
  })
})

describe('clampDelaySeconds', () => {
  it('범위 안의 정수는 그대로 둔다', () => {
    expect(clampDelaySeconds(1)).toBe(1)
    expect(clampDelaySeconds(200)).toBe(200)
    expect(clampDelaySeconds(600)).toBe(600)
  })

  it('범위 밖 값은 가장 가까운 한계로 맞춘다', () => {
    expect(clampDelaySeconds(0)).toBe(1)
    expect(clampDelaySeconds(-5)).toBe(1)
    expect(clampDelaySeconds(601)).toBe(600)
    expect(clampDelaySeconds(100000)).toBe(600)
  })

  it('정수가 아니거나 숫자가 아니면 반올림하거나 기본값 200으로 돌린다', () => {
    expect(clampDelaySeconds(12.6)).toBe(13)
    expect(clampDelaySeconds(Number.NaN)).toBe(200)
    expect(clampDelaySeconds('abc')).toBe(200)
    expect(clampDelaySeconds('30')).toBe(30)
  })
})

describe('clampElapsedSeconds', () => {
  it('0~180으로 맞춘다 (시계 어긋남 보정)', () => {
    expect(clampElapsedSeconds(42.9)).toBe(42)
    expect(clampElapsedSeconds(-3)).toBe(0)
    expect(clampElapsedSeconds(180)).toBe(180)
    expect(clampElapsedSeconds(999)).toBe(180)
    expect(clampElapsedSeconds(Number.NaN)).toBe(0)
  })
})

// 시간 시험의 판정 통계: 예열 20회를 버리고 100회를 재서 중앙값·p99·최댓값을 남긴다.
// 합격은 p99가 한도 이하이고 최댓값이 한도의 maxFactor배 이하다. 재실행으로 가리지 않는다.

export const WARMUP_RUNS = 20
export const MEASURED_RUNS = 100

/** 가까운 순위(nearest-rank) 백분위수. */
export function percentile(samples, fraction) {
  if (samples.length === 0) {
    throw new Error('표본이 없습니다.')
  }
  const sorted = [...samples].sort((a, b) => a - b)
  const rank = Math.max(1, Math.ceil(fraction * sorted.length))
  return sorted[rank - 1]
}

export function summarize(samples) {
  return {
    count: samples.length,
    median: percentile(samples, 0.5),
    p99: percentile(samples, 0.99),
    max: Math.max(...samples),
  }
}

const round = (value) => Math.round(value * 100) / 100

/**
 * 요약을 출력하고 판정 결과를 돌려준다.
 * @param {string} label 구간 이름
 * @param {number[]} samples 측정 표본(ms), 예열은 이미 버린 것
 * @param {number} limitMs p99 한도
 * @param {number} [maxFactor] 최댓값 한도는 limitMs * maxFactor
 */
export function judge(label, samples, limitMs, maxFactor = 2) {
  const summary = summarize(samples)
  const maxLimit = limitMs * maxFactor
  console.info(
    `[시간] ${label}: n=${summary.count} 중앙값=${round(summary.median)}ms p99=${round(summary.p99)}ms ` +
      `최댓값=${round(summary.max)}ms (한도 p99<=${limitMs}ms, 최댓값<=${maxLimit}ms)`,
  )
  return { ...summary, limitMs, maxLimit }
}

/** 예열 구간을 버리고 남은 표본만 돌려준다. */
export function dropWarmup(samples, warmup = WARMUP_RUNS) {
  return samples.slice(warmup)
}

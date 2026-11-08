// 표시 형식. 화면은 점수를 계산하지 않고 서버가 준 값을 보기 좋게 바꾸기만 한다 (BR2.3).

export const DEFAULT_DELAY_SECONDS = 200
export const MIN_DELAY_SECONDS = 1
export const MAX_DELAY_SECONDS = 600
export const MAX_ELAPSED_SECONDS = 180

/** 천 단위 구분 기호. @param {number} amount */
export function formatWon(amount) {
  return String(amount).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** "N시간 N원" (price는 이용 시간 전체 합계). */
export function formatPriceLine(durationHours, price) {
  return `${durationHours}시간 ${formatWon(price)}원`
}

export function formatDistance(distanceMeters) {
  return `${distanceMeters}m`
}

export function formatScore(score) {
  return `종합 ${score}점`
}

/** @param {'BEST'|'CANDIDATE'} rank */
export function rankLabel(rank) {
  return rank === 'BEST' ? '최적 추천' : '비교 후보'
}

/** 시연 설정의 지연 초를 1~600 정수로 맞춘다. 숫자가 아니면 기본값 200 (F11). */
export function clampDelaySeconds(value) {
  const number = Number(value)
  if (value === '' || value === null || !Number.isFinite(number)) {
    return DEFAULT_DELAY_SECONDS
  }
  return Math.min(MAX_DELAY_SECONDS, Math.max(MIN_DELAY_SECONDS, Math.round(number)))
}

/** 경과 초를 0~180 정수로 맞춘다 (브라우저와 서버 시계가 어긋나도 음수나 180 초과를 보이지 않는다). */
export function clampElapsedSeconds(seconds) {
  if (!Number.isFinite(seconds)) {
    return 0
  }
  return Math.min(MAX_ELAPSED_SECONDS, Math.max(0, Math.floor(seconds)))
}

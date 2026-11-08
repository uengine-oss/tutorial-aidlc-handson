// 안내 문구. 앞의 "(!)"는 ErrorNotice가 붙인다.

export const MSG_MISSING_PLACE = '장소를 알려 주세요. 예: "강남역 근처 2시간"'
export const MSG_MISSING_DURATION = '이용 시간을 알려 주세요. 예: "강남역 근처 2시간"'
export const MSG_EMPTY = '조건에 맞는 주차권이 없습니다. 장소나 시간을 바꿔 다시 말해 주세요.'
export const MSG_SEARCH_ERROR = '잠시 후 다시 시도해 주세요.'
export const MSG_PAY_ERROR = '결제 요청을 접수하지 못했습니다. 다시 눌러 주세요.'
export const MSG_PASS_UNAVAILABLE = '선택한 주차권을 지금은 쓸 수 없습니다. 다시 검색해 주세요.'
export const MSG_PAYMENT_REJECTED = '결제가 거절되었습니다.'
export const MSG_REASSIGN_HINT = '다른 주차권으로 다시 결제할 수 있습니다.'
export const MSG_LOADING = '찾는 중...'
export const MSG_WAITING_CONFIRM = '승인 여부를 확인하는 중입니다...'

// 복구 안내 (F10)
export const MSG_STREAM_UNSTABLE = '실시간 연결이 불안정합니다. 새로 고침해 주세요'
export const MSG_SESSION_UNKNOWN = '세션을 확인하지 못했습니다. 새로 고침해 주세요'
export const MSG_RESERVATION_LOAD_FAILED = '예약 상태를 불러오지 못했습니다. 새로 고침해 주세요'

// 서버 message가 비어 있을 때만 쓰는 기본 안내
export const DEFAULT_RESERVATION_MESSAGES = {
  pending: '결제 승인을 기다리는 중입니다...',
  confirmed: '예약이 완료되었습니다',
  failed: '',
  cancelled: '승인 지연으로 예약이 취소되었습니다.',
}

export const QUERY_EXAMPLE = '예: "강남역 근처 2시간 주차권 찾아줘"'

/** 추천 건수 안내 (aria-live). */
export function recommendationCountMessage(count) {
  return `추천 ${count}건`
}

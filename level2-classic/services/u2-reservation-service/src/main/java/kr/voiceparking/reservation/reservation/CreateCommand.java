package kr.voiceparking.reservation.reservation;

/** 예약 생성 요청 값 (C5). 형식 검증은 서비스가 한다 (BR1.1). */
public record CreateCommand(String sessionId, String passId, Integer durationHours, String idempotencyKey) {
}

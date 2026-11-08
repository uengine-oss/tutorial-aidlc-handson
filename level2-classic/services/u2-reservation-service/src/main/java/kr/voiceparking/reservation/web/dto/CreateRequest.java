package kr.voiceparking.reservation.web.dto;

/** POST /internal/reservations 본문 (C5). 값의 검증은 서비스가 한다. */
public record CreateRequest(String sessionId, String passId, Integer durationHours, String idempotencyKey) {
}

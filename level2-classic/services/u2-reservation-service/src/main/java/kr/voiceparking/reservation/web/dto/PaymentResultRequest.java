package kr.voiceparking.reservation.web.dto;

/** POST /internal/reservations/{id}/payment-result 본문 (C5). */
public record PaymentResultRequest(String eventId, String outcome) {
}

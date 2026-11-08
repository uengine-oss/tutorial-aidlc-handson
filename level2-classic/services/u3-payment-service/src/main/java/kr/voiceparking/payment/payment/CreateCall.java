package kr.voiceparking.payment.payment;

/** u2 예약 생성(C5) 호출 값. 금액은 보내지 않는다. u2가 정한 값을 받는다 (BR4.1). */
public record CreateCall(String sessionId, String passId, int durationHours, String idempotencyKey) {
}

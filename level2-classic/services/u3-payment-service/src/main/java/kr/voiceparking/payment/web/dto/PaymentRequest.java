package kr.voiceparking.payment.web.dto;

/** POST /api/payments 본문 (C2). 값의 검증은 서비스가 한다. 정의하지 않은 필드(금액 등)는 무시한다. */
public record PaymentRequest(String passId, Integer durationHours, String idempotencyKey, String mockScenario,
        Integer mockDelaySeconds) {
}

package com.voiceparking.payment.domain;

public record PaymentView(
        String id,
        String reservationId,
        int amount,
        PaymentStatus status,
        String failureReason,
        boolean retryable) {
}

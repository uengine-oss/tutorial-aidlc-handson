package com.voiceparking.payment.service;

/** 이미 승인된 예약에 다시 결제하려는 경우 등. HTTP 402 + PAYMENT_ALREADY_APPROVED. */
public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException(String message) {
        super(message);
    }
}

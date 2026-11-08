package com.voiceparking.payment.service;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String id) {
        super("결제를 찾을 수 없습니다: " + id);
    }
}

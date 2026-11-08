package kr.voiceparking.payment.payment;

import java.time.Instant;

/** 결제 요청의 결과. status는 u2가 돌려준 예약의 현재 상태이고, 결제 대기일 때만 접수(202)로 본다. */
public record PaymentReceipt(String paymentId, String reservationId, String status, Instant paymentRequestedAt) {

    static final String PENDING_PAYMENT = "PENDING_PAYMENT";

    public boolean accepted() {
        return PENDING_PAYMENT.equals(status);
    }
}

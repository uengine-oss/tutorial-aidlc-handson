package kr.voiceparking.payment.web.dto;

import java.time.Instant;

import kr.voiceparking.payment.payment.PaymentReceipt;

/** 결제 요청 응답 (C2). status는 u2가 돌려준 예약의 현재 상태다. */
public record PaymentResponse(String paymentId, String reservationId, String status, Instant paymentRequestedAt) {

    public static PaymentResponse of(PaymentReceipt receipt) {
        return new PaymentResponse(receipt.paymentId(), receipt.reservationId(), receipt.status(),
                receipt.paymentRequestedAt());
    }
}

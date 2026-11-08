package com.voiceparking.payment.domain;

public enum PaymentStatus {
    /** 웹훅을 기다리는 중 */
    PENDING,
    /** 승인되고 예약까지 확정됨 */
    APPROVED,
    /** 실패(재결제 가능 여부는 retryable) */
    FAILED,
    /** 이미 취소된 예약에 승인이 와서 무시됨 */
    LATE_APPROVAL_IGNORED
}

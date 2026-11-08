package com.voiceparking.reservation.domain;

public enum ReservationStatus {
    /** 결제 승인 대기 */
    PENDING,
    /** 확정 */
    CONFIRMED,
    /** 취소(승인 마감 초과) */
    CANCELLED;

    public boolean isFinal() {
        return this != PENDING;
    }
}

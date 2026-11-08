package com.voiceparking.payment.domain;

/**
 * 결제. PENDING에서만 다른 상태로 바뀌며, 최종 상태는 다시 바뀌지 않는다(웹훅 멱등성의 근거).
 */
public class Payment {

    private final String id;
    private final String reservationId;
    private final int amount;
    private PaymentStatus status = PaymentStatus.PENDING;
    private String failureReason;
    private boolean retryable;

    public Payment(String id, String reservationId, int amount) {
        this.id = id;
        this.reservationId = reservationId;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public String getReservationId() {
        return reservationId;
    }

    public synchronized PaymentStatus getStatus() {
        return status;
    }

    public synchronized boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public synchronized void markApproved() {
        requirePending(PaymentStatus.APPROVED);
        status = PaymentStatus.APPROVED;
    }

    public synchronized void markFailed(String reason, boolean canRetry) {
        requirePending(PaymentStatus.FAILED);
        status = PaymentStatus.FAILED;
        failureReason = reason;
        retryable = canRetry;
    }

    public synchronized void markLateApprovalIgnored() {
        requirePending(PaymentStatus.LATE_APPROVAL_IGNORED);
        status = PaymentStatus.LATE_APPROVAL_IGNORED;
        failureReason = "예약이 이미 취소되어 승인을 무시했습니다";
        retryable = false;
    }

    private void requirePending(PaymentStatus target) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("결제 " + id + "는 " + status + " 상태에서 " + target + "(으)로 바뀔 수 없습니다");
        }
    }

    public synchronized PaymentView toView() {
        return new PaymentView(id, reservationId, amount, status, failureReason, retryable);
    }
}

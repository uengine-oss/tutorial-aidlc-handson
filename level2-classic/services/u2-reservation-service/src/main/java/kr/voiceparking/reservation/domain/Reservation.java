package kr.voiceparking.reservation.domain;

import java.time.Duration;
import java.time.Instant;

import kr.voiceparking.reservation.notification.Snapshot;

/**
 * 예약 한 건. 불변이며 전이 메서드가 새 값을 돌려준다. 허용된 전이는 결제 대기에서 나가는 세 가지뿐이고
 * 종료 상태에서는 모든 전이 시도가 무시된다(자기 자신을 돌려준다) (BR2.5).
 * 확정 시각, 실패 시각, 취소 시각 가운데 최대 하나만 값을 가진다.
 */
public record Reservation(String reservationId, String sessionId, String idempotencyKey, String passId,
        String lotName, int durationHours, int amount, ReservationStatus status, int version,
        Instant paymentRequestedAt, Instant confirmedAt, Instant failedAt, Instant cancelledAt, String message) {

    /** 새 예약: 결제 대기, version 1 (BR1.5). */
    public static Reservation create(String reservationId, String sessionId, String idempotencyKey, String passId,
            String lotName, int durationHours, int amount, Instant now) {
        return new Reservation(reservationId, sessionId, idempotencyKey, passId, lotName, durationHours, amount,
                ReservationStatus.PENDING_PAYMENT, 1, now, null, null, null,
                ReservationStatus.PENDING_PAYMENT.message());
    }

    public boolean isPending() {
        return status == ReservationStatus.PENDING_PAYMENT;
    }

    /** 마감 시각 = 결제 요청 시각 + 제한 시간 (BR1.5). */
    public Instant deadline(Duration timeout) {
        return paymentRequestedAt.plus(timeout);
    }

    public Reservation confirm(Instant at) {
        return isPending() ? moved(ReservationStatus.CONFIRMED, at, null, null) : this;
    }

    public Reservation paymentFailed(Instant at) {
        return isPending() ? moved(ReservationStatus.PAYMENT_FAILED, null, at, null) : this;
    }

    public Reservation cancel(Instant at) {
        return isPending() ? moved(ReservationStatus.CANCELLED, null, null, at) : this;
    }

    private Reservation moved(ReservationStatus next, Instant confirmed, Instant failed, Instant cancelled) {
        return new Reservation(reservationId, sessionId, idempotencyKey, passId, lotName, durationHours, amount,
                next, version + 1, paymentRequestedAt, confirmed, failed, cancelled, next.message());
    }

    /** 응답과 푸시에 쓰는 공개 형식. 실패 시각은 내부 속성이라 싣지 않는다. */
    public Snapshot toSnapshot() {
        return new Snapshot(reservationId, version, status.name(), lotName, durationHours, amount,
                paymentRequestedAt, confirmedAt, cancelledAt, message);
    }
}

package com.voiceparking.reservation.domain;

import java.time.Instant;

/**
 * 예약. 상태 전이 규칙: PENDING -> CONFIRMED | CANCELLED. 최종 상태에서는 다시 바뀌지 않으며,
 * 취소된 예약은 확정할 수 없다 (FR3.4, FR4.1).
 */
public class Reservation {

    private final String id;
    private final String ticketId;
    private final String ticketName;
    private final int price;
    private final Instant createdAt;
    private final Instant deadlineAt;
    private ReservationStatus status = ReservationStatus.PENDING;
    private Instant confirmedAt;

    public Reservation(String id, String ticketId, String ticketName, int price,
                       Instant createdAt, Instant deadlineAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.ticketName = ticketName;
        this.price = price;
        this.createdAt = createdAt;
        this.deadlineAt = deadlineAt;
    }

    /** 승인 마감에 이르렀거나 지났는가. 정확히 마감 시각도 만료로 본다. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(deadlineAt);
    }

    /**
     * 확정한다. 이미 확정이면 아무 일도 하지 않고 false, 새로 확정하면 true.
     *
     * @throws InvalidTransitionException 이미 취소된 예약인 경우
     */
    public synchronized boolean confirm(Instant now) {
        if (status == ReservationStatus.CONFIRMED) {
            return false;
        }
        if (status == ReservationStatus.CANCELLED) {
            throw new InvalidTransitionException("이미 취소된 예약은 확정할 수 없습니다: " + id);
        }
        status = ReservationStatus.CONFIRMED;
        confirmedAt = now;
        return true;
    }

    /** 대기 중일 때만 취소한다. 취소가 일어났으면 true. */
    public synchronized boolean cancel() {
        if (status != ReservationStatus.PENDING) {
            return false;
        }
        status = ReservationStatus.CANCELLED;
        return true;
    }

    public synchronized ReservationStatus getStatus() {
        return status;
    }

    public String getId() {
        return id;
    }

    public synchronized ReservationView toView() {
        return new ReservationView(id, ticketId, ticketName, price, status, createdAt, deadlineAt, confirmedAt);
    }
}

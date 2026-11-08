package com.voiceparking.reservation.service;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String id) {
        super("예약을 찾을 수 없습니다: " + id);
    }
}

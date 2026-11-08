package kr.voiceparking.reservation.web.dto;

import java.time.Instant;

import kr.voiceparking.reservation.domain.Reservation;

/** 예약 생성 응답 (C5). 201과 200의 본문이 같다. status는 기존 예약의 현재 상태일 수 있다 (D1). */
public record CreateResponse(String reservationId, String status, int amount, int durationHours, String lotName,
        Instant paymentRequestedAt) {

    public static CreateResponse of(Reservation r) {
        return new CreateResponse(r.reservationId(), r.status().name(), r.amount(), r.durationHours(), r.lotName(),
                r.paymentRequestedAt());
    }
}

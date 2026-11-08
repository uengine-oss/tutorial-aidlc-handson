package kr.voiceparking.payment.payment;

import java.time.Instant;

/** u2 예약 생성 응답(C5). created가 true이면 201(새 예약), false이면 200(기존 예약, status는 어떤 상태든 올 수 있다). */
public record ReservationReply(boolean created, String reservationId, String status, int amount, int durationHours,
        String lotName, Instant paymentRequestedAt) {
}

package com.voiceparking.reservation.domain;

import java.time.Instant;

/** 외부로 내보내는 예약 스냅샷(REST 응답과 SSE 이벤트 공용). */
public record ReservationView(
        String id,
        String ticketId,
        String ticketName,
        int price,
        ReservationStatus status,
        Instant createdAt,
        Instant deadlineAt,
        Instant confirmedAt) {
}

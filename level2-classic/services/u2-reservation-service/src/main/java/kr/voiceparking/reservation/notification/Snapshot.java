package kr.voiceparking.reservation.notification;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 계약 C3의 Reservation 형식과 같은 불변 스냅샷. 푸시 이벤트와 조회 응답이 같은 형식이다.
 * 상태는 문자열로 둔다: notification 패키지는 domain을 참조하지 않는다 (NFR4.5).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Snapshot(String reservationId, int version, String status, String lotName, int durationHours,
        int amount, Instant paymentRequestedAt, Instant confirmedAt, Instant cancelledAt, String message) {
}

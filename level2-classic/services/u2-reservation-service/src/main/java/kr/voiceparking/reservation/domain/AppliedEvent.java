package kr.voiceparking.reservation.domain;

import java.time.Instant;

/**
 * 결제 결과 반영이 한 번 내린 판정 기록. 같은 eventId가 다시 오면 이 기록을 그대로 돌려준다 (BR2.1).
 * 한 번 기록한 값은 바뀌지 않는다.
 */
public record AppliedEvent(String eventId, Outcome outcome, ApplyResult result,
        ReservationStatus reservationStatus, Instant decidedAt) {
}

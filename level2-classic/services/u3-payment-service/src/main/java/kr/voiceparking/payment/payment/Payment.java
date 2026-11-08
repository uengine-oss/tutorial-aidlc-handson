package kr.voiceparking.payment.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * 선택한 주차권 한 건에 대한 결제 요청 기록 (불변). PG가 알려 준 결과의 기록이며 사용자에게 보이는 예약 상태의
 * 기준은 u2의 Reservation.status다. 상태는 REQUESTED에서 종료 상태 하나로 한 번만 바뀐다.
 */
public record Payment(String paymentId, String reservationId, String sessionId, String idempotencyKey, String passId,
        int durationHours, int amount, MockScenario mockScenario, int mockDelaySeconds, PaymentStatus status,
        Instant requestedAt) {

    public Payment {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    /** 새 Payment(REQUESTED). paymentId는 서버가 만든다. amount와 requestedAt은 u2 응답 값이다 (BR4.1). */
    public static Payment requested(String reservationId, String sessionId, String idempotencyKey, String passId,
            int durationHours, int amount, MockScenario mockScenario, int mockDelaySeconds, Instant requestedAt) {
        return new Payment(UUID.randomUUID().toString(), reservationId, sessionId, idempotencyKey, passId,
                durationHours, amount, mockScenario, mockDelaySeconds, PaymentStatus.REQUESTED, requestedAt);
    }

    /** REQUESTED에서 종료 상태로 옮긴 새 값을 돌려준다. 이미 종료 상태이면 거부한다. */
    public Payment settle(PaymentStatus target) {
        if (status.isTerminal()) {
            throw new IllegalStateException("payment already " + status);
        }
        if (!target.isTerminal()) {
            throw new IllegalArgumentException("target must be a terminal status");
        }
        return new Payment(paymentId, reservationId, sessionId, idempotencyKey, passId, durationHours, amount,
                mockScenario, mockDelaySeconds, target, requestedAt);
    }
}

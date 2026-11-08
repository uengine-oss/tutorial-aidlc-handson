package kr.voiceparking.payment.payment;

import java.time.Instant;

/** 서명 검증을 통과해 u2에 반영까지 마친 웹훅 이벤트 한 건. 같은 eventId를 다시 처리하지 않으려고 남긴다 (BR3.4). */
public record WebhookEvent(String eventId, String paymentId, Outcome outcome, Instant receivedAt, Handled handled) {

    public WebhookEvent {
        if (handled == Handled.DUPLICATE) {
            throw new IllegalArgumentException("DUPLICATE is never recorded");
        }
    }
}

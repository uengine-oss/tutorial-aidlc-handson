package kr.voiceparking.payment.payment;

import java.time.Instant;

/** 서명과 형식 검증을 마친 웹훅 호출 값 (C6). */
public record WebhookCall(String eventId, String paymentId, Outcome outcome, Instant occurredAt) {
}

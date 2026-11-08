package kr.voiceparking.payment.payment;

/** PG에 보내는 결제 요청 값. 시나리오와 지연은 모의 PG만 쓴다 (BR2.2). */
public record PgPayment(String paymentId, MockScenario mockScenario, int mockDelaySeconds) {
}

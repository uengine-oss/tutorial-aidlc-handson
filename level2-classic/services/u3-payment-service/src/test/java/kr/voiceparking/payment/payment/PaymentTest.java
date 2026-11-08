package kr.voiceparking.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 도메인: Payment 상태 전이(BR2.1, BR3.5)와 WebhookEvent의 handled 값, 모의 PG 요청 값. */
class PaymentTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-11-05T01:00:00Z");

    private static Payment newPayment() {
        return Payment.requested("res-1", "11111111-2222-3333-4444-555555555555", "key-1", "pass-1", 2, 4000,
                MockScenario.APPROVE, 0, REQUESTED_AT);
    }

    @Test
    void aNewPaymentStartsRequestedWithAServerMadeId() {
        Payment payment = newPayment();

        assertThat(payment.status()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.paymentId()).isNotBlank();
        assertThat(newPayment().paymentId()).isNotEqualTo(payment.paymentId());
        assertThat(payment.amount()).isEqualTo(4000);
        assertThat(payment.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(payment.reservationId()).isEqualTo("res-1");
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "APPROVED", "FAILED", "IGNORED" })
    void requestedMovesToEachTerminalStatusAndTheOriginalStaysUnchanged(PaymentStatus target) {
        Payment payment = newPayment();

        Payment settled = payment.settle(target);

        assertThat(settled.status()).isEqualTo(target);
        assertThat(settled.paymentId()).isEqualTo(payment.paymentId());
        assertThat(settled.amount()).isEqualTo(payment.amount());
        assertThat(payment.status()).isEqualTo(PaymentStatus.REQUESTED);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = { "APPROVED", "FAILED", "IGNORED" })
    void aTerminalStatusNeverChanges(PaymentStatus terminal) {
        Payment settled = newPayment().settle(terminal);

        for (PaymentStatus next : PaymentStatus.values()) {
            assertThatThrownBy(() -> settled.settle(next)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(settled.status()).isEqualTo(terminal);
    }

    @Test
    void settlingToRequestedIsRejected() {
        assertThatThrownBy(() -> newPayment().settle(PaymentStatus.REQUESTED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOutcomeMapsToTheMatchingTerminalStatus() {
        assertThat(PaymentStatus.of(Outcome.APPROVED)).isEqualTo(PaymentStatus.APPROVED);
        assertThat(PaymentStatus.of(Outcome.FAILED)).isEqualTo(PaymentStatus.FAILED);
        assertThat(PaymentStatus.REQUESTED.isTerminal()).isFalse();
        assertThat(PaymentStatus.IGNORED.isTerminal()).isTrue();
    }

    @Test
    void aNegativeAmountIsRejected() {
        assertThatThrownBy(() -> Payment.requested("res-1", "s", "k", "p", 1, -1, MockScenario.APPROVE, 0,
                REQUESTED_AT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWebhookEventRecordsOnlyAppliedOrIgnored() {
        WebhookEvent applied = new WebhookEvent("evt-1", "pay-1", Outcome.APPROVED, REQUESTED_AT, Handled.APPLIED);
        WebhookEvent ignored = new WebhookEvent("evt-2", "pay-1", Outcome.FAILED, REQUESTED_AT, Handled.IGNORED);

        assertThat(applied.handled()).isEqualTo(Handled.APPLIED);
        assertThat(ignored.handled()).isEqualTo(Handled.IGNORED);
        assertThatThrownBy(() -> new WebhookEvent("evt-3", "pay-1", Outcome.APPROVED, REQUESTED_AT, Handled.DUPLICATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPgPaymentCarriesTheScenarioForThePaymentOnly() {
        PgPayment pg = new PgPayment("pay-1", MockScenario.DELAY, 30);

        assertThat(pg.paymentId()).isEqualTo("pay-1");
        assertThat(pg.mockScenario()).isEqualTo(MockScenario.DELAY);
        assertThat(pg.mockDelaySeconds()).isEqualTo(30);
    }
}

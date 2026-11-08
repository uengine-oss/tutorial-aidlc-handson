package kr.voiceparking.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import kr.voiceparking.payment.support.FakeReservationClient;
import kr.voiceparking.payment.support.RecordingGateway;
import kr.voiceparking.payment.support.TestClock;

/** 결제 요청 로그, 웹훅 결과 로그, 원인 코드 로그 (NFR5.12~NFR5.14, NFR6.3). */
class PaymentServiceLogTest {

    private static final String SESSION = "11111111-2222-3333-4444-555555555555";

    private final PaymentStore store = new PaymentStore();
    private final FakeReservationClient u2 = new FakeReservationClient();
    private final TestClock clock = new TestClock(Instant.parse("2026-11-05T01:00:00Z"));
    private final PaymentService service = new PaymentService(store, u2, new RecordingGateway(), clock,
            Duration.ofSeconds(7));
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(PaymentService.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private Payment newPayment(String key) {
        PaymentReceipt receipt = service.request(new PaymentCommand(SESSION, "pass-1", 2, key, "DELAY", 30),
                System.nanoTime());
        return store.findById(receipt.paymentId()).orElseThrow();
    }

    private WebhookCall call(Payment payment, String eventId, Outcome outcome) {
        return new WebhookCall(eventId, payment.paymentId(), outcome, clock.instant());
    }

    @Test
    void aCreatedAndAReplayedPaymentRequestEachLogOneLine() {
        Payment payment = newPayment("key-1");
        service.request(new PaymentCommand(SESSION, "pass-1", 2, "key-1", "DELAY", 30), System.nanoTime());

        assertThat(lines()).contains(
                "event=payment_request payment=" + payment.paymentId() + " reservation=" + payment.reservationId()
                        + " result=CREATED scenario=DELAY",
                "event=payment_request payment=" + payment.paymentId() + " reservation=" + payment.reservationId()
                        + " result=REPLAYED scenario=DELAY");
    }

    @Test
    void aU2RejectionIsLoggedWithItsErrorCode() {
        u2.createFailure = new PaymentException(ErrorCode.PENDING_EXISTS, "res-existing");

        try {
            service.request(new PaymentCommand(SESSION, "pass-1", 2, "key-1", "APPROVE", 0), System.nanoTime());
        } catch (PaymentException expected) {
            // 아래에서 로그를 확인한다.
        }

        assertThat(lines()).contains("event=payment_request payment=- reservation=res-existing "
                + "result=PENDING_EXISTS scenario=APPROVE");
    }

    @Test
    void anUpstreamFailureOfAPaymentRequestIsLoggedAsUpstreamFailed() {
        u2.createFailure = new IllegalStateException("boom");

        try {
            service.request(new PaymentCommand(SESSION, "pass-1", 2, "key-1", "FAIL", 0), System.nanoTime());
        } catch (PaymentException expected) {
            // 아래에서 로그를 확인한다.
        }

        assertThat(lines()).contains("event=payment_request payment=- reservation=- result=UPSTREAM_FAILED "
                + "scenario=FAIL");
    }

    @Test
    void everyWebhookLogsOneResultLineWithPaymentReservationEventOutcomeResultAndMs() {
        Payment payment = newPayment("key-1");
        String prefix = "event=webhook payment=" + payment.paymentId() + " reservation=" + payment.reservationId();

        service.handleWebhook(call(payment, "e1", Outcome.APPROVED));
        service.handleWebhook(call(payment, "e1", Outcome.APPROVED));

        assertThat(lines()).anyMatch(l -> l.matches(prefix + " event_id=e1 outcome=APPROVED result=APPLIED ms=\\d+"));
        assertThat(lines()).anyMatch(l -> l.matches(prefix + " event_id=e1 outcome=APPROVED result=DUPLICATE ms=\\d+"));
    }

    @Test
    void anIgnoredEventLogsTheIgnoreWithReservationStatusAndTheResultLine() {
        Payment payment = newPayment("key-1");
        u2.script(new ApplyReply(ApplyResult.REJECTED, "CANCELLED"));

        service.handleWebhook(call(payment, "e1", Outcome.APPROVED));

        assertThat(lines()).anyMatch(l -> l.contains("result=IGNORED") && l.contains("event_id=e1"));
        assertThat(lines()).anyMatch(l -> l.contains("cause=IGNORED") && l.contains("payment=" + payment.paymentId())
                && l.contains("reservation=" + payment.reservationId()) && l.contains("event_id=e1")
                && l.contains("outcome=APPROVED") && l.contains("reservation_status=CANCELLED"));
    }

    @Test
    void anUpstreamFailureLogsTheResultLineAndTheCauseLine() {
        Payment payment = newPayment("key-1");
        u2.script(new PaymentException(ErrorCode.UPSTREAM_FAILED));

        try {
            service.handleWebhook(call(payment, "e1", Outcome.FAILED));
        } catch (PaymentException expected) {
            // 아래에서 로그를 확인한다.
        }

        assertThat(lines()).anyMatch(l -> l.contains("event=webhook") && l.contains("result=UPSTREAM_FAILED")
                && l.contains("outcome=FAILED"));
        assertThat(lines()).anyMatch(l -> l.contains("cause=UPSTREAM_FAILED")
                && l.contains("payment=" + payment.paymentId()));
    }

    @Test
    void controlCharactersInAnEventIdCannotForgeLogLines() {
        Payment payment = newPayment("key-1");

        service.handleWebhook(call(payment, "e1\nevent=forged", Outcome.APPROVED));

        assertThat(lines()).noneMatch(l -> l.contains("\n"));
        assertThat(lines()).anyMatch(l -> l.contains("event_id=e1_event_forged"));
    }
}

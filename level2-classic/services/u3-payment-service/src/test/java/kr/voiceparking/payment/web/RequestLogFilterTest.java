package kr.voiceparking.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import kr.voiceparking.payment.payment.ApplyReply;
import kr.voiceparking.payment.payment.ApplyResult;
import kr.voiceparking.payment.payment.ErrorCode;
import kr.voiceparking.payment.payment.Payment;
import kr.voiceparking.payment.payment.PaymentCommand;
import kr.voiceparking.payment.payment.PaymentException;
import kr.voiceparking.payment.payment.PaymentService;
import kr.voiceparking.payment.payment.PaymentStore;
import kr.voiceparking.payment.support.ApiTest;
import kr.voiceparking.payment.support.FakeReservationClient;
import kr.voiceparking.payment.support.RecordingGateway;
import kr.voiceparking.payment.support.Signing;

/** 요청마다 한 줄, 웹훅 결과·거부·무시 원인 로그. 본문·서명 값·전체 세션 ID는 어디에도 남기지 않는다 (NFR1.8, NFR1.9, NFR5.12~NFR5.14, NFR6.3, NFR6.8). */
@ApiTest
class RequestLogFilterTest {

    private static final String PATH = "/api/payments/webhook";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FakeReservationClient u2;
    @Autowired
    private RecordingGateway gateway;
    @Autowired
    private PaymentStore store;
    @Autowired
    private PaymentService service;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger root;

    @BeforeEach
    void attach() {
        u2.reset();
        gateway.reset();
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detach() {
        root.detachAppender(appender);
    }

    private List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private String all() {
        return String.join("\n", lines());
    }

    private Payment newPayment(String session) {
        return store.findById(service.request(new PaymentCommand(session, "pass-1", 2, "k-" + UUID.randomUUID(),
                "APPROVE", 0), System.nanoTime()).paymentId()).orElseThrow();
    }

    @Test
    void aPaymentRequestLogsMethodPathStatusMsAndTheShortSessionAndNeverTheFullSession() throws Exception {
        String session = UUID.randomUUID().toString();

        mvc.perform(post("/api/payments").header("X-Session-Id", session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\"BODYSECRET-KEY\"}"));

        assertThat(lines()).anyMatch(l -> l.matches("event=request method=POST path=/api/payments status=202 "
                + "ms=\\d+ session=" + session.substring(0, 8)));
        assertThat(all()).doesNotContain(session).doesNotContain("BODYSECRET");
        assertThat(lines()).anyMatch(l -> l.startsWith("event=payment_request payment=") && l.contains("result=CREATED")
                && l.contains("scenario=APPROVE"));
    }

    @Test
    void errorResponsesCarryTheirCodeOnTheSameLine() throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("{}"));

        assertThat(lines()).anyMatch(l -> l.contains("path=/api/payments status=400")
                && l.contains("session=-") && l.endsWith("code=MISSING_SESSION"));
    }

    @Test
    void queryStringsAreNotLoggedAndAnInvalidSessionHeaderCannotForgeLogLines() throws Exception {
        mvc.perform(get("/api/payments").param("secret", "SECRETWORD").header("X-Session-Id", "<bad>\nforged=1"));

        assertThat(all()).doesNotContain("SECRETWORD").doesNotContain("forged");
        assertThat(lines()).anyMatch(l -> l.contains("path=/api/payments status=405") && l.contains("session=-"));
        assertThat(lines()).noneMatch(l -> l.contains("\n"));
    }

    @Test
    void aWebhookRequestLineHasNoSession() throws Exception {
        Payment payment = newPayment(UUID.randomUUID().toString());
        String json = Signing.webhookBody("e-log-1", payment.paymentId(), "APPROVED");

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).header("X-Signature", Signing.sign(json))
                .content(json).header("X-Session-Id", UUID.randomUUID().toString()));

        assertThat(lines()).anyMatch(l -> l.matches("event=request method=POST path=" + PATH
                + " status=200 ms=\\d+ session=-"));
    }

    @Test
    void anAppliedDuplicateAndIgnoredWebhookEachLogAResultLine() throws Exception {
        Payment payment = newPayment(UUID.randomUUID().toString());
        String json = Signing.webhookBody("e-log-2", payment.paymentId(), "APPROVED");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).header("X-Signature", Signing.sign(json))
                    .content(json));
        }
        u2.script(new ApplyReply(ApplyResult.REJECTED, "CONFIRMED"));
        String other = Signing.webhookBody("e-log-3", payment.paymentId(), "FAILED");
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).header("X-Signature", Signing.sign(other))
                .content(other));

        String prefix = "event=webhook payment=" + payment.paymentId() + " reservation=" + payment.reservationId();
        assertThat(lines()).anyMatch(l -> l.matches(prefix + " event_id=e-log-2 outcome=APPROVED result=APPLIED ms=\\d+"));
        assertThat(lines()).anyMatch(l -> l.matches(prefix + " event_id=e-log-2 outcome=APPROVED result=DUPLICATE ms=\\d+"));
        assertThat(lines()).anyMatch(l -> l.matches(prefix + " event_id=e-log-3 outcome=FAILED result=IGNORED ms=\\d+"));
        assertThat(lines()).anyMatch(l -> l.contains("cause=IGNORED") && l.contains("payment=" + payment.paymentId())
                && l.contains("event_id=e-log-3"));
    }

    @Test
    void anUpstreamFailureLogsTheResultAndTheCause() throws Exception {
        Payment payment = newPayment(UUID.randomUUID().toString());
        u2.script(new PaymentException(ErrorCode.UPSTREAM_FAILED));
        String json = Signing.webhookBody("e-log-4", payment.paymentId(), "APPROVED");

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).header("X-Signature", Signing.sign(json))
                .content(json));

        assertThat(lines()).anyMatch(l -> l.contains("event_id=e-log-4") && l.contains("result=UPSTREAM_FAILED"));
        assertThat(lines()).anyMatch(l -> l.contains("cause=UPSTREAM_FAILED") && l.contains("payment=" + payment.paymentId()));
        assertThat(lines()).anyMatch(l -> l.contains("path=" + PATH + " status=502") && l.endsWith("code=UPSTREAM_FAILED"));
    }

    @Test
    void aRejectedSignatureLogsTimeLengthAndReasonButNeverTheBodyOrTheSignature() throws Exception {
        String json = "{\"marker\":\"BODYSECRET\"}";
        String wrongButWellFormed = "sha256=" + "ab12".repeat(16);

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).header("X-Signature", wrongButWellFormed)
                .content(json));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).header("X-Signature", "sha256=MALFORMEDSIG")
                .content(json));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json));

        int length = json.getBytes(StandardCharsets.UTF_8).length;
        assertThat(lines()).anyMatch(l -> l.contains("cause=INVALID_SIGNATURE") && l.contains("reason=MISMATCH")
                && l.contains("body_length=" + length));
        assertThat(lines()).anyMatch(l -> l.contains("cause=INVALID_SIGNATURE") && l.contains("reason=MALFORMED"));
        assertThat(lines()).anyMatch(l -> l.contains("cause=INVALID_SIGNATURE") && l.contains("reason=MISSING"));
        assertThat(all()).doesNotContain("BODYSECRET").doesNotContain("ab12ab12").doesNotContain("MALFORMEDSIG");
        assertThat(appender.list).filteredOn(e -> e.getFormattedMessage().contains("cause=INVALID_SIGNATURE"))
                .allSatisfy(e -> assertThat(e.getTimeStamp()).isPositive());
    }

    @Test
    void theBodyFilterRunsInsideTheRequestLogFilterSoAnOversizedRequestIsStillLogged() throws Exception {
        int log = RequestLogFilter.class.getAnnotation(Order.class).value();
        int body = WebhookBodyFilter.class.getAnnotation(Order.class).value();
        assertThat(body).isGreaterThan(log);

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("x".repeat(9000)));

        assertThat(lines()).anyMatch(l -> l.contains("path=" + PATH + " status=400") && l.endsWith("code=INVALID_REQUEST"));
    }
}

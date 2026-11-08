package kr.voiceparking.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import kr.voiceparking.payment.payment.ApplyReply;
import kr.voiceparking.payment.payment.ApplyResult;
import kr.voiceparking.payment.payment.ErrorCode;
import kr.voiceparking.payment.payment.Payment;
import kr.voiceparking.payment.payment.PaymentCommand;
import kr.voiceparking.payment.payment.PaymentException;
import kr.voiceparking.payment.payment.PaymentService;
import kr.voiceparking.payment.payment.PaymentStatus;
import kr.voiceparking.payment.payment.PaymentStore;
import kr.voiceparking.payment.support.ApiTest;
import kr.voiceparking.payment.support.FakeReservationClient;
import kr.voiceparking.payment.support.RecordingGateway;
import kr.voiceparking.payment.support.Signing;

/** C6 웹훅의 웹 계층: 200 세 종류, 401 네 경우, 400(형식, 8KB), 404, 502 (BR3.1~BR3.6, NFR6.1, NFR6.5). */
@ApiTest
class WebhookControllerTest {

    private static final String PATH = "/api/payments/webhook";
    private static final int LIMIT = 8192;

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

    private Payment payment;

    @BeforeEach
    void createPayment() {
        u2.reset();
        gateway.reset();
        String session = UUID.randomUUID().toString();
        payment = store.findById(service.request(new PaymentCommand(session, "pass-1", 2, "k1", "APPROVE", 0),
                System.nanoTime()).paymentId()).orElseThrow();
        u2.createCalls.set(0);
    }

    private String body(String eventId, String outcome) {
        return Signing.webhookBody(eventId, payment.paymentId(), outcome);
    }

    private MockHttpServletRequestBuilder signed(String json) {
        return post(PATH).contentType(MediaType.APPLICATION_JSON).header("X-Signature", Signing.sign(json))
                .content(json.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions send(String json) throws Exception {
        return mvc.perform(signed(json));
    }

    private String eventId() {
        return "evt-" + UUID.randomUUID();
    }

    @Test
    void aSignedApprovedEventAnswers200AppliedAndApprovesThePayment() throws Exception {
        send(body(eventId(), "APPROVED")).andExpect(status().isOk()).andExpect(jsonPath("$.handled").value("APPLIED"));

        assertThat(store.findById(payment.paymentId()).orElseThrow().status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(u2.applyCalls.get()).isEqualTo(1);
    }

    @Test
    void aSignedFailedEventFailsThePayment() throws Exception {
        send(body(eventId(), "FAILED")).andExpect(status().isOk()).andExpect(jsonPath("$.handled").value("APPLIED"));

        assertThat(store.findById(payment.paymentId()).orElseThrow().status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void theSameEventAgainAnswers200DuplicateWithoutCallingU2Again() throws Exception {
        String json = body(eventId(), "APPROVED");
        send(json).andExpect(jsonPath("$.handled").value("APPLIED"));

        send(json).andExpect(status().isOk()).andExpect(jsonPath("$.handled").value("DUPLICATE"));
        send(json).andExpect(status().isOk()).andExpect(jsonPath("$.handled").value("DUPLICATE"));

        assertThat(u2.applyCalls.get()).isEqualTo(1);
    }

    @Test
    void whenU2RejectsTheAnswerIs200Ignored() throws Exception {
        u2.script(new ApplyReply(ApplyResult.REJECTED, "CANCELLED"));

        send(body(eventId(), "APPROVED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.handled").value("IGNORED"));

        assertThat(store.findById(payment.paymentId()).orElseThrow().status()).isEqualTo(PaymentStatus.IGNORED);
    }

    @Test
    void aMissingSignatureAnswers401AndChangesNothing() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("e-1", "APPROVED")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));

        assertNothingChanged("e-1");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "sha256=abc", "sha1=0000000000000000000000000000000000000000",
            "sha256=0000000000000000000000000000000000000000000000000000000000000000",
            "SHA256=0000000000000000000000000000000000000000000000000000000000000000" })
    void aMalformedOrWrongSignatureAnswers401AndChangesNothing(String header) throws Exception {
        MockHttpServletRequestBuilder request = post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(body("e-2", "APPROVED"));
        if (header != null) {
            request.header("X-Signature", header);
        }

        mvc.perform(request).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));

        assertNothingChanged("e-2");
    }

    @Test
    void aTamperedBodyAnswers401() throws Exception {
        String original = body("e-3", "FAILED");
        String tampered = original.replace("FAILED", "APPROVED");

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).header("X-Signature", Signing.sign(original))
                .content(tampered)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));

        assertNothingChanged("e-3");
    }

    @Test
    void theSignatureIsCheckedBeforeTheBodyIsParsed() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).header("X-Signature", "sha256=" + "0".repeat(64))
                .content("this is not json")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = { "not json", "[]", "{}", "{\"eventId\":\"e\"}",
            "{\"eventId\":\"\",\"paymentId\":\"p\",\"outcome\":\"APPROVED\",\"occurredAt\":\"2026-11-05T01:00:05Z\"}",
            "{\"eventId\":\"e\",\"paymentId\":\"p\",\"outcome\":\"MAYBE\",\"occurredAt\":\"2026-11-05T01:00:05Z\"}",
            "{\"eventId\":\"e\",\"paymentId\":\"p\",\"outcome\":\"APPROVED\",\"occurredAt\":\"yesterday\"}",
            "{\"eventId\":\"e\",\"paymentId\":\"p\",\"outcome\":\"APPROVED\"}",
            "{\"eventId\":5,\"paymentId\":\"p\",\"outcome\":\"APPROVED\",\"occurredAt\":\"2026-11-05T01:00:05Z\"}",
            "{\"eventId\":\"e\",\"paymentId\":\"p\",\"outcome\":\"APPROVED\",\"occurredAt\":\"2026-11-05T01:00:05Z\"} x" })
    void aSignedButMalformedBodyAnswers400InvalidRequest(String json) throws Exception {
        send(json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(u2.applyCalls.get()).isZero();
    }

    @Test
    void anEventIdOverOneHundredCharactersAnswers400AndOneHundredIsAccepted() throws Exception {
        send(body("x".repeat(101), "APPROVED")).andExpect(status().isBadRequest());

        send(body("x".repeat(100), "APPROVED")).andExpect(status().isOk());
    }

    @Test
    void aBodyOverEightKilobytesAnswers400EvenWithAValidSignature() throws Exception {
        String big = body("e-big", "APPROVED").replace("}", ",\"pad\":\"" + "x".repeat(LIMIT) + "\"}");

        send(big).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertNothingChanged("e-big");
    }

    @Test
    void theSizeCheckComesBeforeTheSignatureCheck() throws Exception {
        String big = "x".repeat(LIMIT + 1);

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).header("X-Signature", "sha256=" + "0".repeat(64))
                .content(big)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void aBodyOfExactlyEightKilobytesIsStillProcessed() throws Exception {
        String base = body("e-edge", "APPROVED");
        int padding = LIMIT - base.getBytes(StandardCharsets.UTF_8).length - ",\"pad\":\"\"".length();
        String exact = base.replace("}", ",\"pad\":\"" + "x".repeat(padding) + "\"}");
        assertThat(exact.getBytes(StandardCharsets.UTF_8)).hasSize(LIMIT);

        send(exact).andExpect(status().isOk()).andExpect(jsonPath("$.handled").value("APPLIED"));
    }

    @Test
    void anUnknownPaymentAnswers404UnknownPayment() throws Exception {
        send(Signing.webhookBody("e-4", "no-such-payment", "APPROVED")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PAYMENT"));

        assertThat(u2.applyCalls.get()).isZero();
    }

    @Test
    void whenU2FailsTheAnswerIs502AndTheEventIsNotRecorded() throws Exception {
        u2.script(new PaymentException(ErrorCode.UPSTREAM_FAILED));

        send(body("e-5", "APPROVED")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_FAILED"));

        assertThat(store.findEvent("e-5")).isEmpty();
        assertThat(store.findById(payment.paymentId()).orElseThrow().status()).isEqualTo(PaymentStatus.REQUESTED);
    }

    @Test
    void noSessionHeaderIsNeededForAWebhook() throws Exception {
        send(body(eventId(), "APPROVED")).andExpect(status().isOk());
    }

    private void assertNothingChanged(String eventId) {
        assertThat(u2.applyCalls.get()).isZero();
        assertThat(store.findEvent(eventId)).isEmpty();
        assertThat(store.findById(payment.paymentId()).orElseThrow().status()).isEqualTo(PaymentStatus.REQUESTED);
    }
}

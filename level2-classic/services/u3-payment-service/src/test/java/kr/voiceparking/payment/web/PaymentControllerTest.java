package kr.voiceparking.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import kr.voiceparking.payment.payment.ErrorCode;
import kr.voiceparking.payment.payment.PaymentException;
import kr.voiceparking.payment.payment.PaymentStatus;
import kr.voiceparking.payment.payment.PaymentStore;
import kr.voiceparking.payment.support.ApiTest;
import kr.voiceparking.payment.support.FakeReservationClient;
import kr.voiceparking.payment.support.RecordingGateway;

/** C2 결제 요청의 웹 계층: 202, 200, 400 두 종류, 404, 409, 422, 502 (BR1.1~BR1.4, NFR6.4). */
@ApiTest
class PaymentControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FakeReservationClient u2;
    @Autowired
    private RecordingGateway gateway;
    @Autowired
    private PaymentStore store;

    private final String session = UUID.randomUUID().toString();

    @BeforeEach
    void reset() {
        u2.reset();
        gateway.reset();
    }

    private static String body(String key) {
        return "{\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\"" + key + "\"}";
    }

    private ResultActions pay(String json) throws Exception {
        return mvc.perform(post("/api/payments").header("X-Session-Id", session)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void aNewPaymentRequestAnswers202WithPendingPaymentAndRegistersWithThePg() throws Exception {
        pay("{\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\"k1\",\"mockScenario\":\"DELAY\","
                + "\"mockDelaySeconds\":30}")
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paymentId").isNotEmpty())
                .andExpect(jsonPath("$.reservationId").value("res-1"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentRequestedAt").value("2026-11-05T01:00:00Z"));

        assertThat(u2.createCalls.get()).isEqualTo(1);
        assertThat(gateway.registered()).hasSize(1);
        assertThat(gateway.registered().get(0).mockDelaySeconds()).isEqualTo(30);
    }

    @Test
    void theClientSuppliedAmountIsIgnored() throws Exception {
        u2.amount = 7700;

        pay("{\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\"k1\",\"amount\":1}")
                .andExpect(status().isAccepted());

        assertThat(store.findByKey(session, "k1").orElseThrow().amount()).isEqualTo(7700);
    }

    @Test
    void aRepeatedKeyWhileStillPendingAnswers202WithTheSamePaymentId() throws Exception {
        String first = pay(body("k1")).andReturn().getResponse().getContentAsString();

        pay(body("k1")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paymentId").value(extract(first, "paymentId")));

        assertThat(gateway.count()).isEqualTo(1);
    }

    @Test
    void aRepeatedKeyAfterTheReservationEndedAnswers200WithTheCurrentStatus() throws Exception {
        pay(body("k1")).andExpect(status().isAccepted());
        u2.currentStatus = "CANCELLED";

        pay(body("k1")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(gateway.count()).isEqualTo(1);
    }

    @Test
    void aMissingOrMalformedSessionHeaderAnswers400MissingSession() throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(body("k1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_SESSION"));
        mvc.perform(post("/api/payments").header("X-Session-Id", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON).content(body("k1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_SESSION"));

        assertThat(u2.createCalls.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"durationHours\":2,\"idempotencyKey\":\"k1\"}",
            "{\"passId\":\"pass-1\",\"idempotencyKey\":\"k1\"}",
            "{\"passId\":\"pass-1\",\"durationHours\":0,\"idempotencyKey\":\"k1\"}",
            "{\"passId\":\"pass-1\",\"durationHours\":25,\"idempotencyKey\":\"k1\"}",
            "{\"passId\":\"pass-1\",\"durationHours\":2.5,\"idempotencyKey\":\"k1\"}",
            "{\"passId\":\"pass-1\",\"durationHours\":\"abc\",\"idempotencyKey\":\"k1\"}",
            "{\"passId\":\"pass-1\",\"durationHours\":2}",
            "{\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\"k1\",\"mockScenario\":\"BOGUS\"}",
            "{\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\"k1\",\"mockDelaySeconds\":601}",
            "{\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\"k1\",\"mockDelaySeconds\":-1}",
            "{\"passId\":\"\",\"durationHours\":2,\"idempotencyKey\":\"k1\"}",
            "not json", "", "[]" })
    void aMalformedBodyAnswers400InvalidRequestAndNothingIsCalled(String json) throws Exception {
        pay(json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(u2.createCalls.get()).isZero();
        assertThat(gateway.count()).isZero();
    }

    @Test
    void theErrorBodyNeverEchoesTheInput() throws Exception {
        String response = pay("{\"passId\":\"LEAKME-PASS\",\"durationHours\":99,\"idempotencyKey\":\"LEAKME-KEY\"}")
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("LEAKME").doesNotContain("99");
        assertThat(response).contains("\"code\"").contains("\"message\"");
    }

    @Test
    void u2Relays404409And422WithTheSameCode() throws Exception {
        u2.createFailure = new PaymentException(ErrorCode.PASS_NOT_FOUND);
        pay(body("k1")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PASS_NOT_FOUND"));

        u2.createFailure = new PaymentException(ErrorCode.PENDING_EXISTS, "res-77");
        pay(body("k2")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PENDING_EXISTS"))
                .andExpect(jsonPath("$.reservationId").value("res-77"));

        u2.createFailure = new PaymentException(ErrorCode.DURATION_NOT_AVAILABLE);
        pay(body("k3")).andExpect(status().is(422)).andExpect(jsonPath("$.code").value("DURATION_NOT_AVAILABLE"));

        assertThat(gateway.count()).isZero();
        assertThat(store.findByKey(session, "k1")).isEmpty();
    }

    @Test
    void anyOtherU2FailureAnswers502UpstreamFailedWithoutAPayment() throws Exception {
        u2.createFailure = new IllegalStateException("u2 down");

        pay(body("k1")).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("UPSTREAM_FAILED"));

        assertThat(store.findByKey(session, "k1")).isEmpty();
        assertThat(gateway.count()).isZero();
    }

    @Test
    void aNewKeyAfterAFailedPaymentIsAccepted() throws Exception {
        pay(body("k1")).andExpect(status().isAccepted());
        store.save(store.findByKey(session, "k1").orElseThrow().settle(PaymentStatus.FAILED));

        pay(body("k2")).andExpect(status().isAccepted());

        assertThat(gateway.count()).isEqualTo(2);
    }

    @Test
    void getOnThePaymentPathIsNotAllowed() throws Exception {
        mvc.perform(get("/api/payments")).andExpect(status().isMethodNotAllowed());
    }

    private static String extract(String json, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        if (!m.find()) {
            throw new AssertionError(field + " missing in " + json);
        }
        return m.group(1);
    }
}

package com.voiceparking.payment.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceparking.payment.client.ReservationClient;
import com.voiceparking.payment.client.ReservationClient.ConfirmResult;
import com.voiceparking.payment.client.ReservationClientException;
import com.voiceparking.payment.mockpg.WebhookSender;
import com.voiceparking.payment.service.WebhookResult;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @MockBean
    ReservationClient reservationClient;
    @MockBean
    WebhookSender webhookSender;

    private String createPayment(String reservationId) throws Exception {
        String body = "{\"reservationId\":\"" + reservationId + "\",\"amount\":6000}";
        String res = mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(res).get("id").asText();
    }

    private void webhook(String paymentId, String result, int expectedStatus) throws Exception {
        String body = "{\"paymentId\":\"" + paymentId + "\",\"result\":\"" + result + "\"}";
        mvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void 승인_웹훅이면_결제가_APPROVED가_된다() throws Exception {
        when(reservationClient.confirm("r-ok")).thenReturn(ConfirmResult.CONFIRMED);
        String id = createPayment("r-ok");
        webhook(id, "APPROVED", 200);
        mvc.perform(get("/api/payments/" + id)).andExpect(jsonPath("$.status").value("APPROVED"));
        verify(reservationClient).confirm("r-ok");
    }

    @Test
    void 취소된_예약에_늦은_승인이면_LATE_APPROVAL_IGNORED() throws Exception {
        when(reservationClient.confirm("r-late")).thenReturn(ConfirmResult.CANCELLED);
        String id = createPayment("r-late");
        webhook(id, "APPROVED", 200);
        mvc.perform(get("/api/payments/" + id))
                .andExpect(jsonPath("$.status").value("LATE_APPROVAL_IGNORED"));
    }

    @Test
    void 모르는_결제_ID_웹훅은_402와_표준_오류_객체() throws Exception {
        String body = "{\"paymentId\":\"unknown-payment\",\"result\":\"APPROVED\"}";
        mvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void 웹훅_본문이_잘못되면_402와_PAYMENT_VALIDATION_FAILED() throws Exception {
        mvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"x\",\"result\":\"WHATEVER\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\":\"\",\"amount\":0}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void 예약_서비스_장애면_402이고_결제는_PENDING() throws Exception {
        when(reservationClient.confirm("r-down")).thenThrow(new ReservationClientException("down"));
        String id = createPayment("r-down");
        String body = "{\"paymentId\":\"" + id + "\",\"result\":\"APPROVED\"}";
        mvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("RESERVATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/api/payments/" + id)).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void 모의_PG_승인_버튼은_비동기로_승인_웹훅을_보낸다() throws Exception {
        String id = createPayment("r-pg");
        mvc.perform(post("/api/mock-pg/" + id + "/approve")).andExpect(status().isAccepted());
        verify(webhookSender, timeout(2000)).send(eq(id), eq(WebhookResult.APPROVED), isNull());
    }

    @Test
    void 모의_PG_실패_버튼은_실패_웹훅을_보낸다() throws Exception {
        String id = createPayment("r-pg2");
        mvc.perform(post("/api/mock-pg/" + id + "/fail")).andExpect(status().isAccepted());
        verify(webhookSender, timeout(2000)).send(eq(id), eq(WebhookResult.FAILED), anyString());
    }

    @Test
    void 모의_PG는_모르는_결제에_402() throws Exception {
        mvc.perform(post("/api/mock-pg/nope/approve"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        mvc.perform(post("/api/mock-pg/nope/fail"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void 이미_승인된_예약에_다시_결제하면_402와_PAYMENT_ALREADY_APPROVED() throws Exception {
        when(reservationClient.confirm("r-paid")).thenReturn(ConfirmResult.CONFIRMED);
        String id = createPayment("r-paid");
        webhook(id, "APPROVED", 200);
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\":\"r-paid\",\"amount\":6000}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_APPROVED"));
    }

    @Test
    void 예상치_못한_예외도_402와_PAYMENT_INTERNAL_ERROR이고_내부정보는_숨긴다() throws Exception {
        when(reservationClient.confirm("r-boom")).thenThrow(new IllegalStateException("secret-internal"));
        String id = createPayment("r-boom");
        String body = "{\"paymentId\":\"" + id + "\",\"result\":\"APPROVED\"}";
        mvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-internal"))));
    }
}

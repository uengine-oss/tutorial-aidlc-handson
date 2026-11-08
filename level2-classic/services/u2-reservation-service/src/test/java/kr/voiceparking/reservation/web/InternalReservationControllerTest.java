package kr.voiceparking.reservation.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.voiceparking.reservation.reservation.ErrorCode;
import kr.voiceparking.reservation.reservation.ReservationException;
import kr.voiceparking.reservation.support.ApiTest;
import kr.voiceparking.reservation.support.FakeSearchClient;

/** C5: 예약 생성(201, 200, 400, 404, 409, 422, 502)과 결제 결과 반영(200, 400, 404). */
@ApiTest
class InternalReservationControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FakeSearchClient search;
    @Autowired
    private ObjectMapper mapper;

    private String session;

    @BeforeEach
    void freshSession() {
        search.reset();
        session = UUID.randomUUID().toString();
    }

    @AfterEach
    void resetSearch() {
        search.reset();
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/internal/reservations").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createJson(String key) {
        return "{\"sessionId\":\"" + session + "\",\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\""
                + key + "\"}";
    }

    private ResultActions result(String reservationId, String json) throws Exception {
        return mvc.perform(post("/internal/reservations/" + reservationId + "/payment-result")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createAndGetId(String key) throws Exception {
        String body = create(createJson(key)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        JsonNode node = mapper.readTree(body);
        return node.get("reservationId").asText();
    }

    @Test
    void createReturns201WithTheContractFields() throws Exception {
        create(createJson("key-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").isString())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.amount").value(6000))
                .andExpect(jsonPath("$.durationHours").value(2))
                .andExpect(jsonPath("$.lotName").value("강남 주차장"))
                .andExpect(jsonPath("$.paymentRequestedAt").isString());
    }

    @Test
    void sameKeyReturns200WithTheSameReservationAndItsCurrentStatus() throws Exception {
        String id = createAndGetId("key-1");
        result(id, "{\"eventId\":\"e-1\",\"outcome\":\"APPROVED\"}").andExpect(status().isOk());

        create(createJson("key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(id))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void anotherKeyWhilePendingIs409WithTheExistingReservationId() throws Exception {
        String id = createAndGetId("key-1");

        create(createJson("key-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PENDING_EXISTS"))
                .andExpect(jsonPath("$.reservationId").value(id))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void malformedCreateRequestsAre400WithoutEchoingTheInput() throws Exception {
        String[] bodies = {
                "{}",
                "{\"sessionId\":\"nope\",\"passId\":\"p\",\"durationHours\":2,\"idempotencyKey\":\"k\"}",
                "{\"sessionId\":\"" + session + "\",\"passId\":\"p\",\"durationHours\":0,\"idempotencyKey\":\"k\"}",
                "{\"sessionId\":\"" + session + "\",\"passId\":\"p\",\"durationHours\":25,\"idempotencyKey\":\"k\"}",
                "{\"sessionId\":\"" + session + "\",\"passId\":\" \",\"durationHours\":2,\"idempotencyKey\":\"k\"}",
                "{\"sessionId\":\"" + session + "\",\"passId\":\"p\",\"durationHours\":\"SECRETWORD\","
                        + "\"idempotencyKey\":\"k\"}",
                "not json SECRETWORD" };
        for (String body : bodies) {
            create(body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(content().string(not(containsString("SECRETWORD"))));
        }
    }

    @Test
    void passNotFoundDurationNotAvailableAndUpstreamFailureArePassedThrough() throws Exception {
        search.behave((p, h) -> {
            throw new ReservationException(ErrorCode.PASS_NOT_FOUND);
        });
        create(createJson("k-1")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PASS_NOT_FOUND"));

        search.behave((p, h) -> {
            throw new ReservationException(ErrorCode.DURATION_NOT_AVAILABLE);
        });
        create(createJson("k-2")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DURATION_NOT_AVAILABLE"));

        search.behave((p, h) -> {
            throw new ReservationException(ErrorCode.UPSTREAM_FAILED);
        });
        create(createJson("k-3")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_FAILED"));
    }

    @Test
    void approvalIsAppliedThenDuplicateAndSecondApprovalBehaveAsContracted() throws Exception {
        String id = createAndGetId("key-1");

        result(id, "{\"eventId\":\"e-1\",\"outcome\":\"APPROVED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("APPLIED"))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));
        result(id, "{\"eventId\":\"e-1\",\"outcome\":\"APPROVED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("APPLIED"))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));
        result(id, "{\"eventId\":\"e-2\",\"outcome\":\"APPROVED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("REJECTED"))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));
    }

    @Test
    void failedOutcomeMovesToPaymentFailed() throws Exception {
        String id = createAndGetId("key-1");

        result(id, "{\"eventId\":\"e-1\",\"outcome\":\"FAILED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("APPLIED"))
                .andExpect(jsonPath("$.reservationStatus").value("PAYMENT_FAILED"));
    }

    @Test
    void malformedResultRequestsAre400BeforeTheReservationIsLookedUp() throws Exception {
        String id = createAndGetId("key-1");
        String longId = "e".repeat(101);
        String[] bodies = { "{}", "{\"eventId\":\"e-1\"}", "{\"outcome\":\"APPROVED\"}",
                "{\"eventId\":\"e-1\",\"outcome\":\"MAYBE\"}", "{\"eventId\":\"\",\"outcome\":\"APPROVED\"}",
                "{\"eventId\":\"" + longId + "\",\"outcome\":\"APPROVED\"}", "garbage" };
        for (String body : bodies) {
            result(id, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        result("missing", "{\"eventId\":\"e-1\",\"outcome\":\"MAYBE\"}").andExpect(status().isBadRequest());
    }

    @Test
    void resultForAnUnknownReservationIs404() throws Exception {
        result("missing", "{\"eventId\":\"e-1\",\"outcome\":\"APPROVED\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}

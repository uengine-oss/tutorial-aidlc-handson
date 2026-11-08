package kr.voiceparking.reservation.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import kr.voiceparking.reservation.domain.Outcome;
import kr.voiceparking.reservation.reservation.CreateCommand;
import kr.voiceparking.reservation.reservation.ReservationService;
import kr.voiceparking.reservation.support.ApiTest;
import kr.voiceparking.reservation.support.FakeSearchClient;

/** C3 조회: 현재 예약, 단건 조회, 세션 필수와 세션 격리 (BR3.1~BR3.3, NFR6.2, NFR6.3). */
@ApiTest
class ReservationControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ReservationService service;

    @Autowired
    private FakeSearchClient search;

    private String session = UUID.randomUUID().toString();

    @BeforeEach
    void resetSearch() {
        search.reset();
    }

    private String createReservation(String forSession) {
        return service.create(new CreateCommand(forSession, "pass-1", 2, UUID.randomUUID().toString()))
                .reservation().reservationId();
    }

    @Test
    void currentReturnsTheLatestReservationInTheContractShape() throws Exception {
        String id = createReservation(session);

        mvc.perform(get("/api/reservations/current").header("X-Session-Id", session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(id))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.lotName").value("강남 주차장"))
                .andExpect(jsonPath("$.durationHours").value(2))
                .andExpect(jsonPath("$.amount").value(6000))
                .andExpect(jsonPath("$.paymentRequestedAt").isString())
                .andExpect(jsonPath("$.message").value("결제 승인을 기다리는 중입니다"))
                .andExpect(jsonPath("$.confirmedAt").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist());
    }

    @Test
    void currentShowsConfirmedAtOnlyWhenConfirmed() throws Exception {
        String id = createReservation(session);
        service.applyPaymentResult(id, "e-1", Outcome.APPROVED);

        mvc.perform(get("/api/reservations/current").header("X-Session-Id", session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.confirmedAt").isString())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.message").value("예약이 완료되었습니다"));
    }

    @Test
    void currentWithoutAnyReservationIsNoReservation() throws Exception {
        mvc.perform(get("/api/reservations/current").header("X-Session-Id", session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_RESERVATION"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void missingOrMalformedSessionIs401() throws Exception {
        mvc.perform(get("/api/reservations/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_SESSION"));
        mvc.perform(get("/api/reservations/current").header("X-Session-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_SESSION"));
        mvc.perform(get("/api/reservations/some-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_SESSION"));
    }

    @Test
    void querySessionIsNotAcceptedOutsideTheSubscriptionEndpoint() throws Exception {
        mvc.perform(get("/api/reservations/current").param("sessionId", session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void singleReservationIsReturnedToItsOwner() throws Exception {
        String id = createReservation(session);

        mvc.perform(get("/api/reservations/" + id).header("X-Session-Id", session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(id))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void anotherSessionsReservationLooksLikeItDoesNotExist() throws Exception {
        String id = createReservation(session);

        mvc.perform(get("/api/reservations/" + id).header("X-Session-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/reservations/missing").header("X-Session-Id", session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void errorBodiesNeverEchoTheInput() throws Exception {
        mvc.perform(get("/api/reservations/<script>alert(1)</script>").header("X-Session-Id", session))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("script"))))
                .andExpect(content().string(not(containsString(session))));
        mvc.perform(get("/api/reservations/current").header("X-Session-Id", "<b>bad</b>"))
                .andExpect(content().string(not(containsString("<b>"))));
    }
}

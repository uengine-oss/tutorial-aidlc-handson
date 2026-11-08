package kr.voiceparking.reservation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import kr.voiceparking.reservation.domain.Outcome;
import kr.voiceparking.reservation.reservation.CreateCommand;
import kr.voiceparking.reservation.reservation.ReservationService;
import kr.voiceparking.reservation.support.ApiTest;
import kr.voiceparking.reservation.support.FakeSearchClient;

/** C3 구독: 연결하면 최신 스냅샷을 먼저 한 번, 이후 변화를 이어서. 세션 규칙과 헤더 우선 (BR3.1, BR3.2, BR4.3). */
@ApiTest
class EventsControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ReservationService service;

    @Autowired
    private FakeSearchClient search;

    private final String session = UUID.randomUUID().toString();

    @BeforeEach
    void resetSearch() {
        search.reset();
    }

    private String createReservation() {
        return service.create(new CreateCommand(session, "pass-1", 2, UUID.randomUUID().toString()))
                .reservation().reservationId();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void connectingSendsTheCurrentSnapshotFirstAsNamedEventWithVersionAsId() throws Exception {
        String id = createReservation();

        MvcResult result = mvc.perform(get("/api/reservations/" + id + "/events").header("X-Session-Id", session))
                .andExpect(request().asyncStarted()).andReturn();

        await().atMost(3, TimeUnit.SECONDS).until(() -> body(result).contains("data:"));
        String stream = body(result);
        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(stream).contains("event:snapshot").contains("id:1");
        assertThat(stream).contains("\"reservationId\":\"" + id + "\"").contains("\"status\":\"PENDING_PAYMENT\"")
                .contains("\"version\":1").doesNotContain("confirmedAt");
    }

    @Test
    void laterStateChangesArePushedOnTheSameConnection() throws Exception {
        String id = createReservation();
        MvcResult result = mvc.perform(get("/api/reservations/" + id + "/events").header("X-Session-Id", session))
                .andExpect(request().asyncStarted()).andReturn();
        await().atMost(3, TimeUnit.SECONDS).until(() -> body(result).contains("id:1"));

        service.applyPaymentResult(id, "e-1", Outcome.APPROVED);

        await().atMost(3, TimeUnit.SECONDS).until(() -> body(result).contains("id:2"));
        assertThat(body(result)).contains("\"status\":\"CONFIRMED\"").contains("\"confirmedAt\"")
                .contains("예약이 완료되었습니다");
    }

    @Test
    void reconnectingAfterATerminalStateStillGetsThatStateOnce() throws Exception {
        String id = createReservation();
        service.applyPaymentResult(id, "e-1", Outcome.FAILED);

        MvcResult result = mvc.perform(get("/api/reservations/" + id + "/events").header("X-Session-Id", session))
                .andExpect(request().asyncStarted()).andReturn();

        await().atMost(3, TimeUnit.SECONDS).until(() -> body(result).contains("data:"));
        assertThat(body(result)).contains("id:2").contains("PAYMENT_FAILED");
        assertThat(body(result).split("event:snapshot", -1)).hasSize(2);
    }

    @Test
    void sessionCanComeFromTheQueryParameter() throws Exception {
        String id = createReservation();

        MvcResult result = mvc.perform(get("/api/reservations/" + id + "/events").param("sessionId", session))
                .andExpect(request().asyncStarted()).andReturn();

        await().atMost(3, TimeUnit.SECONDS).until(() -> body(result).contains("data:"));
    }

    @Test
    void theHeaderWinsOverTheQueryParameter() throws Exception {
        String id = createReservation();
        String stranger = UUID.randomUUID().toString();

        // 헤더가 소유자이면 쿼리가 낯선 세션이어도 연결된다.
        MvcResult ok = mvc.perform(get("/api/reservations/" + id + "/events").header("X-Session-Id", session)
                .param("sessionId", stranger)).andExpect(request().asyncStarted()).andReturn();
        await().atMost(3, TimeUnit.SECONDS).until(() -> body(ok).contains("data:"));

        // 헤더가 낯선 세션이면 쿼리가 소유자여도 404다.
        mvc.perform(get("/api/reservations/" + id + "/events").header("X-Session-Id", stranger)
                .param("sessionId", session)).andExpect(status().isNotFound());
    }

    @Test
    void missingOrMalformedSessionIs401EvenWhenTheClientAcceptsEventStream() throws Exception {
        String id = createReservation();

        mvc.perform(get("/api/reservations/" + id + "/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_SESSION"));
        mvc.perform(get("/api/reservations/" + id + "/events").param("sessionId", "nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_SESSION"));
    }

    @Test
    void anotherSessionsOrMissingReservationIs404BeforeAnyConnectionOpens() throws Exception {
        String id = createReservation();

        mvc.perform(get("/api/reservations/" + id + "/events").header("X-Session-Id", UUID.randomUUID().toString())
                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/reservations/missing/events").header("X-Session-Id", session))
                .andExpect(status().isNotFound());
    }
}

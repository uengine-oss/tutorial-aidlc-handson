package kr.voiceparking.reservation.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import kr.voiceparking.reservation.reservation.CreateCommand;
import kr.voiceparking.reservation.reservation.ReservationService;
import kr.voiceparking.reservation.support.FakeSearchConfig;

/** 동시 구독 수 상한을 넘으면 503 SUBSCRIPTION_LIMIT (D7). */
@SpringBootTest(properties = "reservation.push.max-subscriptions=2")
@AutoConfigureMockMvc
@Import(FakeSearchConfig.class)
class EventsSubscriptionLimitTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ReservationService service;

    @Test
    void theSubscriptionBeyondTheCapIs503() throws Exception {
        String session = UUID.randomUUID().toString();
        String id = service.create(new CreateCommand(session, "pass-1", 2, "key-1")).reservation().reservationId();

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/reservations/" + id + "/events").header("X-Session-Id", session))
                    .andExpect(request().asyncStarted());
        }

        mvc.perform(get("/api/reservations/" + id + "/events").header("X-Session-Id", session))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_LIMIT"));
    }
}

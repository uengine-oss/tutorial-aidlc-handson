package com.voiceparking.reservation.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    private String createReservation() throws Exception {
        String body = "{\"ticketId\":\"T001\",\"ticketName\":\"강남역 공영\",\"price\":6000}";
        MvcResult res = mvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode json = mapper.readTree(res.getResponse().getContentAsByteArray());
        return json.get("id").asText();
    }

    @Test
    void 예약을_만들고_내부_확정_API로_확정한다() throws Exception {
        String id = createReservation();
        mvc.perform(post("/internal/reservations/" + id + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(get("/api/reservations/" + id))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void 모르는_예약은_404() throws Exception {
        mvc.perform(post("/internal/reservations/unknown/confirm")).andExpect(status().isNotFound());
        mvc.perform(get("/api/reservations/unknown")).andExpect(status().isNotFound());
    }

    @Test
    void 잘못된_예약_요청은_400() throws Exception {
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"\",\"ticketName\":\"x\",\"price\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 취소된_예약을_확정하면_409() throws Exception {
        // 시계 검증은 서비스 단위 테스트가 맡는다. 여기서는 취소 상태 -> HTTP 409 매핑만 확인한다.
        String id = createReservation();
        cancel(id);
        mvc.perform(post("/internal/reservations/" + id + "/confirm"))
                .andExpect(status().isConflict());
    }

    @Autowired
    com.voiceparking.reservation.repository.ReservationRepository repository;

    private com.voiceparking.reservation.domain.Reservation findReservation(String id) {
        return repository.findById(id).orElseThrow();
    }

    private void cancel(String id) {
        findReservation(id).cancel();
    }

    @Test
    void SSE_구독_중_확정되면_1초_안에_CONFIRMED_이벤트가_도착한다() throws Exception {
        String id = createReservation();
        MvcResult stream = mvc.perform(get("/api/reservations/" + id + "/events"))
                .andExpect(request().asyncStarted())
                .andReturn();

        long start = System.nanoTime();
        mvc.perform(post("/internal/reservations/" + id + "/confirm")).andExpect(status().isOk());

        String content = "";
        long deadline = start + 1_000_000_000L;
        while (System.nanoTime() < deadline && !content.contains("CONFIRMED")) {
            content = stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
            Thread.sleep(10);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(content.contains("event:status"), "SSE status 이벤트 없음: " + content);
        assertTrue(content.contains("CONFIRMED"), "CONFIRMED 이벤트가 1초 안에 도착하지 않음: " + content);
        assertTrue(elapsedMs < 1000, "NFR1 위반: " + elapsedMs + "ms");
    }
}

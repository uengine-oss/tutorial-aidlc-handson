package kr.voiceparking.reservation.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import kr.voiceparking.reservation.support.ApiTest;
import kr.voiceparking.reservation.support.FakeSearchClient;

/** 처리하지 못한 예외는 500 INTERNAL_ERROR 공통 형식으로, 프레임워크 오류도 같은 형식으로 낸다 (NFR5.6, NFR6.6). */
@ApiTest
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FakeSearchClient search;

    @AfterEach
    void reset() {
        search.reset();
    }

    @Test
    void unexpectedExceptionBecomes500WithAFixedBody() throws Exception {
        search.behave((p, h) -> {
            throw new IllegalStateException("boom internal detail 10.0.0.7");
        });
        String body = "{\"sessionId\":\"" + UUID.randomUUID()
                + "\",\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\"k\"}";

        mvc.perform(post("/internal/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(content().string(not(containsString("boom"))))
                .andExpect(content().string(not(containsString("10.0.0.7"))));
    }

    @Test
    void frameworkErrorsUseTheCommonShapeToo() throws Exception {
        mvc.perform(get("/internal/reservations"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").isString());
        mvc.perform(post("/internal/reservations").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/no/such/path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}

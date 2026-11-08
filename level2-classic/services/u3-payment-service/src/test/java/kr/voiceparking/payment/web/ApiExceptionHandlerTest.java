package kr.voiceparking.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.voiceparking.payment.support.ApiTest;
import kr.voiceparking.payment.support.FakeReservationClient;
import kr.voiceparking.payment.support.RecordingGateway;

/** 처리하지 못한 예외는 500 INTERNAL_ERROR와 공통 오류 형식으로 낸다. 본문에는 입력 원문도 내부 정보도 없다 (NFR5.8, NFR6.8). */
@ApiTest
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FakeReservationClient u2;
    @Autowired
    private RecordingGateway gateway;
    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void reset() {
        u2.reset();
        gateway.reset();
    }

    @Test
    void anUnexpectedExceptionAnswers500InternalErrorInTheCommonFormatWithoutInternals() throws Exception {
        gateway.failure = new IllegalStateException("db password is hunter2 at com.internal.Secret");

        String response = mvc.perform(post("/api/payments").header("X-Session-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passId\":\"INPUT-ECHO\",\"durationHours\":2,\"idempotencyKey\":\"k1\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty()).andReturn().getResponse().getContentAsString();

        JsonNode json = mapper.readTree(response);
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder("code", "message");
        assertThat(response).doesNotContain("hunter2").doesNotContain("com.internal").doesNotContain("INPUT-ECHO")
                .doesNotContain("IllegalStateException");
    }

    @Test
    void frameworkErrorsUseTheSameFormat() throws Exception {
        mvc.perform(get("/api/payments")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED")).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/payments").header("X-Session-Id", UUID.randomUUID().toString())
                .contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/nowhere")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void anUnreadableBodyAnswers400InvalidRequestWithoutParserDetails() throws Exception {
        String response = mvc.perform(post("/api/payments").header("X-Session-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"passId\": TOKEN-LEAK"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("TOKEN-LEAK").doesNotContain("Unrecognized").doesNotContain("line:");
    }
}

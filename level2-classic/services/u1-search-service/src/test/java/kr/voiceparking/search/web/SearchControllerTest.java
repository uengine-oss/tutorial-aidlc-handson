package kr.voiceparking.search.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    private static final String PATH = "/api/search/recommendations";

    @Autowired
    private MockMvc mvc;

    private ResultActions search(String sessionId, String body) throws Exception {
        var request = post(PATH).contentType(MediaType.APPLICATION_JSON).content(body);
        if (sessionId != null) {
            request.header("X-Session-Id", sessionId);
        }
        return mvc.perform(request);
    }

    private ResultActions searchText(String text) throws Exception {
        return search(UUID.randomUUID().toString(), "{\"text\":\"" + text + "\"}");
    }

    @Test
    void returnsBestAndCandidatesForAValidRequest() throws Exception {
        searchText("강남역 근처 2시간 주차권 찾아줘")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.place").value("강남역"))
                .andExpect(jsonPath("$.query.durationHours").value(2))
                .andExpect(jsonPath("$.recommendations", hasSize(3)))
                .andExpect(jsonPath("$.recommendations[0].passId").value("gangnam-public-1"))
                .andExpect(jsonPath("$.recommendations[0].lotName").value("강남역 공영주차장"))
                .andExpect(jsonPath("$.recommendations[0].price").value(6000))
                .andExpect(jsonPath("$.recommendations[0].distanceMeters").value(120))
                .andExpect(jsonPath("$.recommendations[0].score").value(62))
                .andExpect(jsonPath("$.recommendations[0].rank").value("BEST"))
                .andExpect(jsonPath("$.recommendations[0].reason.topFactor").value("DISTANCE"))
                .andExpect(jsonPath("$.recommendations[0].reason.text", containsString("거리")))
                .andExpect(jsonPath("$.recommendations[1].rank").value("CANDIDATE"))
                .andExpect(jsonPath("$.recommendations[2].rank").value("CANDIDATE"));
    }

    @Test
    void returnsEmptyListWhenNoPassCoversTheDuration() throws Exception {
        searchText("홍대입구역 5시간")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.place").value("홍대입구역"))
                .andExpect(jsonPath("$.recommendations", hasSize(0)));
    }

    @Test
    void missingPlaceIsUnprocessableAndDoesNotEchoInput() throws Exception {
        searchText("근처 2시간 주차권 XYZZY")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MISSING_PLACE"))
                .andExpect(jsonPath("$.message").value("장소를 알려 주세요"))
                .andExpect(content().string(not(containsString("XYZZY"))));
    }

    @Test
    void missingDurationIsUnprocessable() throws Exception {
        searchText("강남역 근처 주차권")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MISSING_DURATION"));
    }

    @Test
    void emptyOrOversizedOrMissingTextIsBadRequest() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        search(sessionId, "{\"text\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TEXT"));
        search(sessionId, "{\"text\":\"" + "가".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TEXT"));
        search(sessionId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TEXT"));
        search(sessionId, "{ not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TEXT"));
    }

    @Test
    void textOfExactlyTwoHundredCharactersIsAccepted() throws Exception {
        String text = "강남역 2시간 " + "가".repeat(200 - "강남역 2시간 ".length());
        assertThat(text).hasSize(200);
        searchText(text).andExpect(status().isOk());
    }

    @Test
    void missingOrMalformedSessionHeaderIsBadRequest() throws Exception {
        search(null, "{\"text\":\"강남역 2시간\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SESSION"));
        search("not-a-uuid", "{\"text\":\"강남역 2시간\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SESSION"));
    }
}

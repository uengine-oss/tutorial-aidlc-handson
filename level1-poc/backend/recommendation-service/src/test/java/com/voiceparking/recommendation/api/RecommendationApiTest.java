package com.voiceparking.recommendation.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RecommendationApiTest {

    @Autowired
    MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions recommend(String text) throws Exception {
        String body = "{\"text\":\"" + text + "\"}";
        return mvc.perform(post("/api/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void 강남역_2시간은_후보_3개와_최적_설명을_돌려준다() throws Exception {
        recommend("강남역 근처 2시간 주차권 찾아줘")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.candidates", hasSize(3)))
                .andExpect(jsonPath("$.candidates[0].best").value(true))
                .andExpect(jsonPath("$.candidates[0].reason", not(emptyOrNullString())))
                .andExpect(jsonPath("$.candidates[1].best").value(false))
                .andExpect(jsonPath("$.candidates[1].reason").doesNotExist())
                .andExpect(jsonPath("$.notice").doesNotExist());
    }

    @Test
    void 후보가_3개_미만이면_있는_만큼과_안내_문구() throws Exception {
        recommend("홍대입구역 2시간 주차")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates", hasSize(2)))
                .andExpect(jsonPath("$.notice", containsString("부족")));
    }

    @Test
    void 알아듣지_못하면_재입력_안내() throws Exception {
        recommend("주차권 찾아줘")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEED_RETRY"))
                .andExpect(jsonPath("$.candidates", hasSize(0)));
    }

    @Test
    void 반경_안에_후보가_없으면_NO_RESULT() throws Exception {
        recommend("부산역 2시간")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_RESULT"));
    }

    @Test
    void 빈_text는_400() throws Exception {
        recommend("  ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", not(emptyOrNullString())));
    }
}

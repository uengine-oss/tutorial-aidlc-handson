package kr.voiceparking.search.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class InternalPassControllerTest {

    @Autowired
    private MockMvc mvc;

    private ResultActions lookup(String passId, String durationHours) throws Exception {
        var request = get("/internal/passes/{passId}", passId);
        if (durationHours != null) {
            request.param("durationHours", durationHours);
        }
        return mvc.perform(request);
    }

    @Test
    void returnsPassPriceForTheWholeDuration() throws Exception {
        lookup("gangnam-public-1", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passId").value("gangnam-public-1"))
                .andExpect(jsonPath("$.lotName").value("강남역 공영주차장"))
                .andExpect(jsonPath("$.durationHours").value(2))
                .andExpect(jsonPath("$.price").value(6000));
    }

    @Test
    void unknownPassIsNotFound() throws Exception {
        lookup("nope", "2")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PASS_NOT_FOUND"));
    }

    @Test
    void passThatCannotCoverTheDurationIsUnprocessable() throws Exception {
        lookup("gangnam-private-1", "2")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DURATION_NOT_AVAILABLE"));
    }

    @Test
    void missingOrInvalidDurationIsBadRequest() throws Exception {
        for (String bad : new String[] {null, "abc", "0", "-1", "25", "1.5", "99999999999"}) {
            lookup("gangnam-public-1", bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DURATION"));
        }
    }

    @Test
    void durationIsValidatedBeforeThePassIsLooked() throws Exception {
        lookup("nope", "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DURATION"));
    }

    @Test
    void repeatingTheSameRequestGivesTheSameAnswer() throws Exception {
        String first = lookup("teheran-1", "3").andReturn().getResponse().getContentAsString();
        String second = lookup("teheran-1", "3").andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }
}

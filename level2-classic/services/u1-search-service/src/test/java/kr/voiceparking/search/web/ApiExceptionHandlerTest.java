package kr.voiceparking.search.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.voiceparking.search.service.ErrorCode;
import kr.voiceparking.search.service.SearchException;

class ApiExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("db password=hunter2 leaked");
        }

        @GetMapping("/expected")
        String expected() {
            throw new SearchException(ErrorCode.PASS_NOT_FOUND);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void unexpectedExceptionBecomesInternalErrorWithoutLeakingDetails() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다"))
                .andExpect(content().string(not(containsString("hunter2"))));
    }

    @Test
    void expectedExceptionUsesItsStatusAndFixedMessage() throws Exception {
        mvc.perform(get("/expected"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PASS_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("주차권을 찾을 수 없습니다"));
    }

    @Test
    void frameworkErrorsKeepTheirStatusAndTheCommonShape() throws Exception {
        mvc.perform(post("/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").exists());
    }
}

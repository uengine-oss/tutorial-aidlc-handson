package kr.voiceparking.search.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import kr.voiceparking.search.service.ErrorCode;
import kr.voiceparking.search.service.SearchException;

class RequestLogFilterTest {

    private static final String SESSION = "12345678-aaaa-bbbb-cccc-1234567890ab";

    @RestController
    static class StubController {

        @PostMapping("/ok")
        String ok(@RequestBody String body) {
            return "{}";
        }

        @GetMapping("/fail")
        String fail() {
            throw new SearchException(ErrorCode.MISSING_PLACE);
        }
    }

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
        appender.start();
        logger.addAppender(appender);
        mvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestLogFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void logsPathStatusDurationAndShortSessionOnly() throws Exception {
        mvc.perform(post("/ok").header("X-Session-Id", SESSION)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"비밀 입력 원문 PRIVATE-TEXT\"}"));

        assertThat(lines()).hasSize(1);
        String line = lines().get(0);
        assertThat(line).contains("path=/ok", "status=200", "durationMs=", "session=12345678");
        assertThat(line).doesNotContain("PRIVATE-TEXT").doesNotContain(SESSION);
        assertThat(line).doesNotContain("errorCode=");
    }

    @Test
    void logsTheErrorCodeForFailedRequests() throws Exception {
        mvc.perform(get("/fail").header("X-Session-Id", SESSION));

        assertThat(lines()).hasSize(1);
        assertThat(lines().get(0)).contains("path=/fail", "status=422", "errorCode=MISSING_PLACE");
    }

    @Test
    void ignoresNonUuidSessionValues() throws Exception {
        mvc.perform(post("/ok").header("X-Session-Id", "evil\nforged line")
                .contentType(MediaType.APPLICATION_JSON).content("{}"));

        assertThat(lines()).hasSize(1);
        assertThat(lines().get(0)).contains("session=-").doesNotContain("evil").doesNotContain("\n");
    }
}

package kr.voiceparking.reservation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import kr.voiceparking.reservation.support.ApiTest;

/** 요청마다 한 줄: 메서드, 경로(쿼리 제외), 상태, 시간, 세션 앞 8자, 오류 코드 (NFR1.6, NFR1.7, NFR6.6). */
@ApiTest
class RequestLogFilterTest {

    @Autowired
    private MockMvc mvc;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void logsMethodPathStatusDurationAndShortSession() throws Exception {
        String session = UUID.randomUUID().toString();

        mvc.perform(get("/api/reservations/current").header("X-Session-Id", session));

        assertThat(lines()).anyMatch(l -> l.matches("event=request method=GET path=/api/reservations/current "
                + "status=404 ms=\\d+ session=" + session.substring(0, 8) + " code=NO_RESERVATION"));
        assertThat(String.join("\n", lines())).doesNotContain(session);
    }

    @Test
    void doesNotLogQueryStringsAndOnlyShortensTheQuerySession() throws Exception {
        String session = UUID.randomUUID().toString();

        mvc.perform(get("/api/reservations/missing/events").param("sessionId", session).param("secret", "SECRETWORD"));

        String all = String.join("\n", lines());
        assertThat(all).contains("path=/api/reservations/missing/events").contains("session=" + session.substring(0, 8));
        assertThat(all).doesNotContain(session).doesNotContain("SECRETWORD").doesNotContain("?");
    }

    @Test
    void invalidSessionAndRequestBodiesAreNeverLogged() throws Exception {
        mvc.perform(post("/internal/reservations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"passId\":\"BODYSECRET\"}").header("X-Session-Id", "<bad>\nforged=1"));

        assertThat(lines()).anyMatch(l -> l.contains("path=/internal/reservations status=400")
                && l.contains("session=- ") && l.endsWith("code=INVALID_REQUEST"));
        assertThat(String.join("\n", lines())).doesNotContain("BODYSECRET").doesNotContain("forged");
        assertThat(lines()).noneMatch(l -> l.contains("\n"));
    }

    @Test
    void successfulRequestsHaveNoCodeField() throws Exception {
        mvc.perform(get("/actuator/health"));

        assertThat(lines()).anyMatch(l -> l.contains("path=/actuator/health status=200") && !l.contains("code="));
    }
}

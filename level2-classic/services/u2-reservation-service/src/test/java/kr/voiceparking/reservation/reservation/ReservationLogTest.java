package kr.voiceparking.reservation.reservation;

import static kr.voiceparking.reservation.support.ServiceFixture.SESSION;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import kr.voiceparking.reservation.domain.Outcome;
import kr.voiceparking.reservation.support.ServiceFixture;

/** 상태 전이, 거부, 중복 로그 한 줄 (NFR5.9, NFR5.10, NFR5.3). 세션 ID 전체는 남기지 않는다 (NFR6.6). */
class ReservationLogTest {

    private final ServiceFixture fx = new ServiceFixture();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(ReservationService.class);
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

    private String create() {
        return fx.service.create(new CreateCommand(SESSION, "pass-1", 2, "key-1")).reservation().reservationId();
    }

    @Test
    void creationLogsOneStateChangeLine() {
        String id = create();

        assertThat(lines()).contains(
                "event=state_change reservation=" + id + " from=NONE to=PENDING_PAYMENT version=1 cause=CREATED");
    }

    @Test
    void approvalAndFailureLogTheirCause() {
        String id = create();
        fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);

        assertThat(lines()).contains("event=state_change reservation=" + id
                + " from=PENDING_PAYMENT to=CONFIRMED version=2 cause=APPROVED");

        ServiceFixture other = new ServiceFixture();
        String otherId = other.service.create(new CreateCommand(SESSION, "pass-1", 2, "key-1")).reservation()
                .reservationId();
        other.service.applyPaymentResult(otherId, "e-1", Outcome.FAILED);
        assertThat(lines()).contains("event=state_change reservation=" + otherId
                + " from=PENDING_PAYMENT to=PAYMENT_FAILED version=2 cause=FAILED");
    }

    @Test
    void timerCancelAndLateResultLogTheirCauses() {
        String id = create();
        fx.clock.advance(Duration.ofSeconds(180));
        fx.scheduler.only().task().run();
        assertThat(lines()).contains("event=state_change reservation=" + id
                + " from=PENDING_PAYMENT to=CANCELLED version=2 cause=AUTO_CANCEL");

        ServiceFixture late = new ServiceFixture();
        String lateId = late.service.create(new CreateCommand(SESSION, "pass-1", 2, "key-1")).reservation()
                .reservationId();
        late.clock.advance(Duration.ofSeconds(181));
        late.service.applyPaymentResult(lateId, "e-1", Outcome.APPROVED);
        assertThat(lines()).contains("event=state_change reservation=" + lateId
                + " from=PENDING_PAYMENT to=CANCELLED version=2 cause=LATE_RESULT");
    }

    @Test
    void rejectedAndDuplicateResultsAreLoggedWithReservationEventOutcomeAndStatus() {
        String id = create();
        fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);
        fx.service.applyPaymentResult(id, "e-2", Outcome.APPROVED);
        fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);

        assertThat(lines()).contains(
                "event=payment_result cause=REJECTED reservation=" + id + " eventId=e-2 outcome=APPROVED status=CONFIRMED",
                "event=payment_result cause=DUPLICATE reservation=" + id + " eventId=e-1 outcome=APPROVED status=CONFIRMED");
    }

    @Test
    void logsNeverContainTheFullSessionId() {
        String id = create();
        fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);

        assertThat(String.join("\n", lines())).doesNotContain(SESSION);
    }

    @Test
    void controlCharactersInEventIdsAreNotWrittenToTheLog() {
        String id = create();
        fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);
        fx.service.applyPaymentResult(id, "bad\nevent=forged", Outcome.APPROVED);

        assertThat(lines()).noneMatch(line -> line.contains("\n"));
    }
}

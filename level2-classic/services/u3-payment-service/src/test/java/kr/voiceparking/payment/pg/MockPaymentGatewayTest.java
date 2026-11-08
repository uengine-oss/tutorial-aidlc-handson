package kr.voiceparking.payment.pg;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import kr.voiceparking.payment.payment.MockScenario;
import kr.voiceparking.payment.payment.PgPayment;
import kr.voiceparking.payment.signature.HmacSigner;
import kr.voiceparking.payment.support.Await;
import kr.voiceparking.payment.support.ManualScheduler;
import kr.voiceparking.payment.support.StubServer;
import kr.voiceparking.payment.support.StubServer.Hit;
import kr.voiceparking.payment.support.TestClock;

/** 모의 PG: 시나리오별 결과와 지연, 서명된 알림, 결제당 한 번, 실패 원인 로그 (BR2.1, NFR1.6, NFR5.7, NFR5.11). */
class MockPaymentGatewayTest {

    private static final String SECRET = "unit-test-signing-value";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WEBHOOK_PATH = "/api/payments/webhook";

    private final HmacSigner signer = new HmacSigner(SECRET);
    private final TestClock clock = new TestClock(Instant.parse("2026-11-05T01:00:00Z"));
    private final ManualScheduler manual = new ManualScheduler();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;
    private StubServer receiver;

    @BeforeEach
    void start() throws IOException {
        logger = (Logger) LoggerFactory.getLogger(MockPaymentGateway.class);
        appender.start();
        logger.addAppender(appender);
        receiver = StubServer.replying(200, "{\"handled\":\"APPLIED\"}");
    }

    @AfterEach
    void stop() {
        logger.detachAppender(appender);
        receiver.close();
    }

    private MockPaymentGateway gateway(StubServer target) {
        return new MockPaymentGateway(clock, manual.scheduler(), Runnable::run,
                new WebhookCaller(target.url() + WEBHOOK_PATH, Duration.ofSeconds(2)), signer);
    }

    private List<String> logLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void approveFiresAnApprovedCallbackRightAway() throws Exception {
        MockPaymentGateway gateway = gateway(receiver);

        gateway.register(new PgPayment("p1", MockScenario.APPROVE, 0));

        assertThat(manual.only().at()).isEqualTo(clock.instant());
        manual.only().task().run();
        assertThat(receiver.hitCount()).isEqualTo(1);
        JsonNode body = JSON.readTree(receiver.hits().get(0).body());
        assertThat(body.get("paymentId").asText()).isEqualTo("p1");
        assertThat(body.get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(UUID.fromString(body.get("eventId").asText())).isNotNull();
        assertThat(Instant.parse(body.get("occurredAt").asText())).isEqualTo(clock.instant());
    }

    @Test
    void theCallbackIsPostedToTheWebhookPathWithAValidSignatureOverTheRawBody() {
        MockPaymentGateway gateway = gateway(receiver);
        gateway.register(new PgPayment("p1", MockScenario.APPROVE, 0));

        manual.only().task().run();

        Hit hit = receiver.hits().get(0);
        assertThat(hit.method()).isEqualTo("POST");
        assertThat(hit.path()).isEqualTo(WEBHOOK_PATH);
        assertThat(hit.signature()).isEqualTo(signer.sign(hit.body().getBytes(StandardCharsets.UTF_8)));
        assertThat(signer.matches(hit.body().getBytes(StandardCharsets.UTF_8), hit.signature())).isTrue();
    }

    @Test
    void failFiresAFailedCallbackRightAway() throws Exception {
        MockPaymentGateway gateway = gateway(receiver);

        gateway.register(new PgPayment("p1", MockScenario.FAIL, 0));

        assertThat(manual.only().at()).isEqualTo(clock.instant());
        manual.only().task().run();
        assertThat(JSON.readTree(receiver.hits().get(0).body()).get("outcome").asText()).isEqualTo("FAILED");
    }

    @Test
    void approveIgnoresTheDelayValue() {
        MockPaymentGateway gateway = gateway(receiver);

        gateway.register(new PgPayment("p1", MockScenario.APPROVE, 120));

        assertThat(manual.only().at()).isEqualTo(clock.instant());
    }

    @Test
    void delayFiresApprovedAfterTheGivenSecondsOfTheInjectedClock() throws Exception {
        MockPaymentGateway gateway = gateway(receiver);

        gateway.register(new PgPayment("p1", MockScenario.DELAY, 30));

        assertThat(manual.only().at()).isEqualTo(clock.instant().plusSeconds(30));
        clock.advance(Duration.ofSeconds(30));
        manual.only().task().run();
        JsonNode body = JSON.readTree(receiver.hits().get(0).body());
        assertThat(body.get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(Instant.parse(body.get("occurredAt").asText())).isEqualTo(clock.instant());
    }

    @Test
    void aDelayOfZeroSecondsMeansTwoHundredSeconds() {
        MockPaymentGateway gateway = gateway(receiver);

        gateway.register(new PgPayment("p1", MockScenario.DELAY, 0));

        assertThat(manual.only().at()).isEqualTo(clock.instant().plusSeconds(200));
        assertThat(MockPaymentGateway.DEFAULT_DELAY_SECONDS).isEqualTo(200);
    }

    @Test
    void aPaymentGetsExactlyOneCallbackEvenIfRegisteredTwice() {
        MockPaymentGateway gateway = gateway(receiver);

        gateway.register(new PgPayment("p1", MockScenario.APPROVE, 0));
        gateway.register(new PgPayment("p1", MockScenario.FAIL, 0));

        assertThat(manual.scheduled()).hasSize(1);
        manual.only().task().run();
        assertThat(receiver.hitCount()).isEqualTo(1);
    }

    @Test
    void everyCallbackGetsAFreshEventId() throws Exception {
        MockPaymentGateway gateway = gateway(receiver);
        gateway.register(new PgPayment("p1", MockScenario.APPROVE, 0));
        gateway.register(new PgPayment("p2", MockScenario.APPROVE, 0));

        manual.scheduled().forEach(s -> s.task().run());

        Set<String> eventIds = new HashSet<>();
        for (Hit hit : receiver.hits()) {
            eventIds.add(JSON.readTree(hit.body()).get("eventId").asText());
        }
        assertThat(eventIds).hasSize(2);
    }

    @Test
    void anErrorResponseIsLoggedAsCallbackFailedAndNeverResent() throws Exception {
        try (StubServer failing = StubServer.replying(502, "{\"code\":\"UPSTREAM_FAILED\"}")) {
            MockPaymentGateway gateway = gateway(failing);
            gateway.register(new PgPayment("p1", MockScenario.APPROVE, 0));

            manual.only().task().run();
            Thread.sleep(300);

            assertThat(failing.hitCount()).isEqualTo(1);
            assertThat(logLines()).anyMatch(l -> l.contains("PG_CALLBACK_FAILED") && l.contains("payment=p1"));
            assertThat(logLines()).noneMatch(l -> l.contains(SECRET));
        }
    }

    @Test
    void aRefusedConnectionIsLoggedAsCallbackFailed() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        MockPaymentGateway gateway = new MockPaymentGateway(clock, manual.scheduler(), Runnable::run,
                new WebhookCaller("http://127.0.0.1:" + closedPort + WEBHOOK_PATH, Duration.ofSeconds(2)), signer);
        gateway.register(new PgPayment("p1", MockScenario.APPROVE, 0));

        manual.only().task().run();

        assertThat(logLines()).anyMatch(l -> l.contains("PG_CALLBACK_FAILED") && l.contains("payment=p1"));
    }

    @Test
    void aTimedOutCallIsLoggedAsNoResponseAndNeverResent() throws Exception {
        try (StubServer silent = new StubServer((exchange, index) -> Thread.sleep(1500))) {
            MockPaymentGateway gateway = new MockPaymentGateway(clock, manual.scheduler(), Runnable::run,
                    new WebhookCaller(silent.url() + WEBHOOK_PATH, Duration.ofMillis(250)), signer);
            gateway.register(new PgPayment("p1", MockScenario.APPROVE, 0));

            manual.only().task().run();
            Thread.sleep(300);

            assertThat(silent.hitCount()).isEqualTo(1);
            assertThat(logLines()).anyMatch(l -> l.contains("PG_CALLBACK_NO_RESPONSE") && l.contains("payment=p1"));
            assertThat(logLines()).noneMatch(l -> l.contains("PG_CALLBACK_FAILED"));
        }
    }

    @Test
    void aRejectedHandOffDropsThatCallbackWithALogAndDoesNotThrow() {
        MockPaymentGateway gateway = new MockPaymentGateway(clock, manual.scheduler(), command -> {
            throw new RejectedExecutionException("queue full");
        }, new WebhookCaller(receiver.url() + WEBHOOK_PATH, Duration.ofSeconds(2)), signer);
        gateway.register(new PgPayment("p1", MockScenario.APPROVE, 0));

        manual.only().task().run();

        assertThat(receiver.hitCount()).isZero();
        assertThat(logLines()).anyMatch(l -> l.contains("PG_CALLBACK_DROPPED") && l.contains("payment=p1"));
    }

    @Test
    void aFullRealDispatchQueueDropsTheOverflowOnly() throws Exception {
        try (StubServer slow = new StubServer((exchange, index) -> {
            Thread.sleep(800);
            StubServer.respond(exchange, 200, "{}");
        })) {
            ThreadPoolExecutor tiny = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<>());
            try {
                MockPaymentGateway gateway = new MockPaymentGateway(clock, manual.scheduler(), tiny,
                        new WebhookCaller(slow.url() + WEBHOOK_PATH, Duration.ofSeconds(2)), signer);
                gateway.register(new PgPayment("p1", MockScenario.APPROVE, 0));
                gateway.register(new PgPayment("p2", MockScenario.APPROVE, 0));

                manual.scheduled().forEach(s -> s.task().run());

                assertThat(logLines()).filteredOn(l -> l.contains("PG_CALLBACK_DROPPED")).hasSize(1);
                assertThat(Await.until(() -> slow.hitCount() == 1, 2000)).isTrue();
            } finally {
                tiny.shutdownNow();
            }
        }
    }

    @Test
    void theRealSchedulerPathDeliversADelayedCallbackAfterAboutTheDelay() throws Exception {
        ThreadPoolTaskScheduler scheduler = realScheduler();
        ThreadPoolExecutor dispatch = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(64));
        try {
            MockPaymentGateway gateway = new MockPaymentGateway(java.time.Clock.systemUTC(), scheduler, dispatch,
                    new WebhookCaller(receiver.url() + WEBHOOK_PATH, Duration.ofSeconds(2)), signer);
            long start = System.nanoTime();

            gateway.register(new PgPayment("p1", MockScenario.DELAY, 2));

            assertThat(Await.until(() -> receiver.hitCount() == 1, 4000)).isTrue();
            long arrivedMs = (receiver.hits().get(0).nanos() - start) / 1_000_000;
            assertThat(arrivedMs).isBetween(1500L, 3000L);
        } finally {
            scheduler.shutdown();
            dispatch.shutdownNow();
        }
    }

    @Test
    void aSlowCallbackDoesNotDelayOtherTimersBecauseTheSchedulerOnlyHandsOff() throws Exception {
        List<Long> arrivals = java.util.Collections.synchronizedList(new ArrayList<>());
        try (StubServer server = new StubServer((exchange, index) -> {
            if (index == 0) {
                Thread.sleep(1500);
            }
            arrivals.add(System.nanoTime());
            StubServer.respond(exchange, 200, "{}");
        })) {
            ThreadPoolTaskScheduler scheduler = realScheduler();
            ThreadPoolExecutor dispatch = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(64));
            try {
                MockPaymentGateway gateway = new MockPaymentGateway(java.time.Clock.systemUTC(), scheduler, dispatch,
                        new WebhookCaller(server.url() + WEBHOOK_PATH, Duration.ofSeconds(3)), signer);
                long start = System.nanoTime();
                gateway.register(new PgPayment("slow", MockScenario.APPROVE, 0));
                Thread.sleep(100);
                gateway.register(new PgPayment("quick", MockScenario.APPROVE, 0));

                assertThat(Await.until(() -> !arrivals.isEmpty(), 1000)).isTrue();
                long firstFinishedMs = (arrivals.get(0) - start) / 1_000_000;
                assertThat(firstFinishedMs).isLessThan(800L);
            } finally {
                scheduler.shutdown();
                dispatch.shutdownNow();
            }
        }
    }

    @Test
    void anApproveCallbackStartsWithinAHundredMillisecondsAfterRegistration() throws Exception {
        ThreadPoolTaskScheduler scheduler = realScheduler();
        ThreadPoolExecutor dispatch = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(64));
        try {
            MockPaymentGateway gateway = new MockPaymentGateway(java.time.Clock.systemUTC(), scheduler, dispatch,
                    new WebhookCaller(receiver.url() + WEBHOOK_PATH, Duration.ofSeconds(2)), signer);
            long worst = 0;
            for (int i = 0; i < 40; i++) {
                int before = receiver.hitCount();
                long registered = System.nanoTime();
                gateway.register(new PgPayment("p" + i, i % 2 == 0 ? MockScenario.APPROVE : MockScenario.FAIL, 0));
                int expected = before + 1;
                assertThat(Await.until(() -> receiver.hitCount() >= expected, 1000)).isTrue();
                if (i >= 10) {
                    worst = Math.max(worst, (receiver.hits().get(before).nanos() - registered) / 1_000_000);
                }
            }
            assertThat(worst).isLessThanOrEqualTo(100L);
        } finally {
            scheduler.shutdown();
            dispatch.shutdownNow();
        }
    }

    private static ThreadPoolTaskScheduler realScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("test-pg-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}

package kr.voiceparking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.web.servlet.MockMvc;

import kr.voiceparking.payment.payment.MockScenario;
import kr.voiceparking.payment.payment.Payment;
import kr.voiceparking.payment.payment.PaymentCommand;
import kr.voiceparking.payment.payment.PaymentService;
import kr.voiceparking.payment.payment.PaymentStore;
import kr.voiceparking.payment.payment.PgPayment;
import kr.voiceparking.payment.pg.MockPaymentGateway;
import kr.voiceparking.payment.pg.WebhookCaller;
import kr.voiceparking.payment.signature.HmacSigner;
import kr.voiceparking.payment.support.ApiTest;
import kr.voiceparking.payment.support.Await;
import kr.voiceparking.payment.support.FakeReservationClient;
import kr.voiceparking.payment.support.RecordingGateway;
import kr.voiceparking.payment.support.Signing;
import kr.voiceparking.payment.support.StubServer;

/**
 * 성능 목표 (NFR1.1~NFR1.6, NFR4.1, NFR4.5): 예열 20회를 버리고 이어지는 100회의 최댓값을 목표와 비교한다. 측정 구간은
 * 요청 필터 진입부터 응답까지이고 u2는 정상 응답하는 가짜(지연 10ms)다. 목표는 완화하지 않는다. 첫 실행이 한도를 넘었을
 * 때는 초과가 단발 이상값(중앙값의 5배 이상)일 때만 한 번 다시 실행한다 (E6).
 */
@ApiTest
class PerformanceTest {

    private static final int WARM_UP = 20;
    private static final int RUNS = 100;
    private static final String WEBHOOK = "/api/payments/webhook";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FakeReservationClient u2;
    @Autowired
    private RecordingGateway gateway;
    @Autowired
    private PaymentStore store;
    @Autowired
    private PaymentService service;

    @BeforeEach
    void reset() {
        u2.reset();
        gateway.reset();
        u2.createDelay = Duration.ofMillis(10);
        u2.applyDelay = Duration.ofMillis(10);
    }

    /** 요청 하나를 만든다. 반복마다 새 값이 필요하면 호출 쪽이 index로 구분한다. */
    @FunctionalInterface
    private interface Step {
        void run(int index) throws Exception;
    }

    private record Stats(double median, double max, List<Double> slowest) {
    }

    private static Stats measure(Step step) throws Exception {
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < WARM_UP + RUNS; i++) {
            long start = System.nanoTime();
            step.run(i);
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            if (i >= WARM_UP) {
                samples.add(ms);
            }
        }
        Collections.sort(samples);
        return new Stats(samples.get(samples.size() / 2), samples.get(samples.size() - 1),
                samples.subList(samples.size() - 3, samples.size()));
    }

    /** E6: 첫 실행이 한도를 넘으면, 초과가 중앙값의 5배 이상인 단발 이상값일 때만 한 번 다시 실행한다. */
    private static void assertMaxWithin(String name, double limitMs, java.util.function.Supplier<Step> stepFactory)
            throws Exception {
        Stats first = measure(stepFactory.get());
        System.out.printf("PERF %s run1 median=%.1fms max=%.1fms limit=%.0fms slowest3=%s%n", name, first.median(),
                first.max(), limitMs, first.slowest());
        if (first.max() <= limitMs) {
            return;
        }
        assertThat(first.max()).as("%s: 초과가 단발 이상값이 아니라 재실행하지 않는다 (median %.1f ms)", name, first.median())
                .isGreaterThanOrEqualTo(first.median() * 5);
        Stats second = measure(stepFactory.get());
        System.out.printf("PERF %s run2 median=%.1fms max=%.1fms limit=%.0fms slowest3=%s%n", name, second.median(),
                second.max(), limitMs, second.slowest());
        assertThat(second.max()).as("%s 재실행", name).isLessThanOrEqualTo(limitMs);
    }

    private List<Payment> createPayments(int count) {
        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String session = UUID.randomUUID().toString();
            payments.add(store.findById(service.request(new PaymentCommand(session, "pass-1", 2, "k" + i, "APPROVE",
                    0), System.nanoTime()).paymentId()).orElseThrow());
        }
        u2.createCalls.set(0);
        return payments;
    }

    private void postWebhook(String json, String signature) throws Exception {
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).header("X-Signature", signature)
                .content(json.getBytes(StandardCharsets.UTF_8))).andReturn();
    }

    @Test
    void aNewWebhookIsHandledWithin150Milliseconds() throws Exception {
        assertMaxWithin("webhook", 150, () -> {
            List<Payment> payments = createPayments(WARM_UP + RUNS);
            return i -> {
                String json = Signing.webhookBody("evt-perf-" + UUID.randomUUID(), payments.get(i).paymentId(),
                        "APPROVED");
                postWebhook(json, Signing.sign(json));
            };
        });
    }

    @Test
    void aDuplicateWebhookIsAnsweredWithin20Milliseconds() throws Exception {
        List<Payment> payments = createPayments(1);
        String json = Signing.webhookBody("evt-dup", payments.get(0).paymentId(), "APPROVED");
        String signature = Signing.sign(json);
        postWebhook(json, signature);
        int appliedBefore = u2.applyCalls.get();

        assertMaxWithin("duplicate", 20, () -> i -> postWebhook(json, signature));

        assertThat(u2.applyCalls.get()).isEqualTo(appliedBefore);
    }

    @Test
    void aRejectedSignatureIsAnsweredWithin20Milliseconds() throws Exception {
        List<Payment> payments = createPayments(1);
        String json = Signing.webhookBody("evt-bad", payments.get(0).paymentId(), "APPROVED");
        String wrong = "sha256=" + "0".repeat(64);

        assertMaxWithin("signature-reject", 20, () -> i -> postWebhook(json, wrong));

        assertThat(u2.applyCalls.get()).isZero();
    }

    @Test
    void aPaymentRequestIsAnsweredWithin500Milliseconds() throws Exception {
        assertMaxWithin("payment-request", 500, () -> {
            String session = UUID.randomUUID().toString();
            return i -> mvc.perform(post("/api/payments").header("X-Session-Id", session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\"perf-" + UUID.randomUUID()
                            + "\"}")).andReturn();
        });
    }

    @Test
    void tenSessionsPayAtTheSameTimeAndEveryAnswerStaysWithin500Milliseconds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<Double>> worst = new ArrayList<>();
            for (int s = 0; s < 10; s++) {
                String session = UUID.randomUUID().toString();
                worst.add(pool.submit(() -> {
                    double max = 0;
                    for (int i = 0; i < 20; i++) {
                        long start = System.nanoTime();
                        int status = mvc.perform(post("/api/payments").header("X-Session-Id", session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\"c-"
                                        + UUID.randomUUID() + "\"}")).andReturn().getResponse().getStatus();
                        assertThat(status).isEqualTo(202);
                        if (i >= 5) {
                            max = Math.max(max, (System.nanoTime() - start) / 1_000_000.0);
                        }
                    }
                    return max;
                }));
            }
            double overall = 0;
            for (Future<Double> f : worst) {
                overall = Math.max(overall, f.get(30, TimeUnit.SECONDS));
            }
            System.out.printf("PERF concurrent-10-sessions max=%.1fms limit=500ms%n", overall);
            assertThat(overall).isLessThanOrEqualTo(500);
            assertThat(gateway.count()).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void fiftyDelayedCallbacksAreSentOnTimeWithinOneSecond() throws Exception {
        List<Long> arrivals = Collections.synchronizedList(new ArrayList<>());
        try (StubServer receiver = new StubServer((exchange, index) -> {
            arrivals.add(System.nanoTime());
            StubServer.respond(exchange, 200, "{}");
        })) {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setDaemon(true);
            scheduler.initialize();
            ThreadPoolExecutor dispatch = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(64));
            try {
                MockPaymentGateway pg = new MockPaymentGateway(Clock.systemUTC(), scheduler, dispatch,
                        new WebhookCaller(receiver.url() + WEBHOOK, Duration.ofSeconds(2)),
                        new HmacSigner(Signing.TEST_SECRET));
                long start = System.nanoTime();
                for (int i = 0; i < 50; i++) {
                    pg.register(new PgPayment("delayed-" + i, MockScenario.DELAY, 5));
                }

                assertThat(Await.until(() -> arrivals.size() == 50, 8000)).isTrue();
                for (long arrival : new ArrayList<>(arrivals)) {
                    assertThat((arrival - start) / 1_000_000).isBetween(4000L, 6000L);
                }
            } finally {
                scheduler.shutdown();
                dispatch.shutdownNow();
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}

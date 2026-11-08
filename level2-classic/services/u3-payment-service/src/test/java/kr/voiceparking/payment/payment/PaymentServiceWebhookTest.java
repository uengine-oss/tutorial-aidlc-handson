package kr.voiceparking.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kr.voiceparking.payment.payment.PaymentStore.Lease;
import kr.voiceparking.payment.payment.PaymentStore.LockScope;
import kr.voiceparking.payment.support.FakeReservationClient;
import kr.voiceparking.payment.support.FakeReservationClient.Apply;
import kr.voiceparking.payment.support.RecordingGateway;
import kr.voiceparking.payment.support.TestClock;

/** 웹훅 처리(BR3.3~BR3.6, NFR5.1, NFR5.2, NFR5.6, E1, E3, E8). 서명과 본문 해석은 웹 계층의 일이라 여기서는 다루지 않는다. */
class PaymentServiceWebhookTest {

    private static final String SESSION = "11111111-2222-3333-4444-555555555555";

    private final PaymentStore store = new PaymentStore();
    private final FakeReservationClient u2 = new FakeReservationClient();
    private final RecordingGateway gateway = new RecordingGateway();
    private final TestClock clock = new TestClock(Instant.parse("2026-11-05T01:00:00Z"));
    private final PaymentService service = new PaymentService(store, u2, gateway, clock, Duration.ofSeconds(7));
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private Payment payment;

    @BeforeEach
    void createPayment() {
        PaymentReceipt receipt = service.request(new PaymentCommand(SESSION, "pass-1", 2, "key-1", "APPROVE", 0),
                System.nanoTime());
        payment = store.findById(receipt.paymentId()).orElseThrow();
        u2.createCalls.set(0);
    }

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    private WebhookCall call(String eventId, Outcome outcome) {
        return new WebhookCall(eventId, payment.paymentId(), outcome, clock.instant());
    }

    private PaymentStatus status() {
        return store.findById(payment.paymentId()).orElseThrow().status();
    }

    private static PaymentException errorOf(Runnable action) {
        try {
            action.run();
        } catch (PaymentException e) {
            return e;
        }
        throw new AssertionError("expected a PaymentException");
    }

    @Test
    void anApprovedNewEventCallsU2OnceMarksTheEventAndApprovesThePayment() {
        Handled handled = service.handleWebhook(call("e1", Outcome.APPROVED));

        assertThat(handled).isEqualTo(Handled.APPLIED);
        assertThat(u2.applies()).containsExactly(new Apply(payment.reservationId(), "e1", Outcome.APPROVED));
        assertThat(status()).isEqualTo(PaymentStatus.APPROVED);
        WebhookEvent event = store.findEvent("e1").orElseThrow();
        assertThat(event.handled()).isEqualTo(Handled.APPLIED);
        assertThat(event.paymentId()).isEqualTo(payment.paymentId());
        assertThat(event.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(event.receivedAt()).isEqualTo(clock.instant());
    }

    @Test
    void aFailedNewEventMakesThePaymentFailed() {
        Handled handled = service.handleWebhook(call("e1", Outcome.FAILED));

        assertThat(handled).isEqualTo(Handled.APPLIED);
        assertThat(u2.applies().get(0).outcome()).isEqualTo(Outcome.FAILED);
        assertThat(status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void theSameEventThreeTimesInARowCallsU2OnceAndTheRestAreDuplicates() {
        List<Handled> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            results.add(service.handleWebhook(call("e1", Outcome.APPROVED)));
        }

        assertThat(results).containsExactly(Handled.APPLIED, Handled.DUPLICATE, Handled.DUPLICATE);
        assertThat(u2.applyCalls.get()).isEqualTo(1);
        assertThat(status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void theSameEventThreeTimesAtOnceCallsU2OnceAndTheRestAreDuplicates() throws Exception {
        for (int round = 0; round < 5; round++) {
            String eventId = "concurrent-" + round;
            int before = u2.applyCalls.get();
            u2.applyDelay = Duration.ofMillis(60);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Handled>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return service.handleWebhook(call(eventId, Outcome.APPROVED));
                }));
            }
            start.countDown();
            List<Handled> results = new ArrayList<>();
            for (Future<Handled> future : futures) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }

            assertThat(results).containsExactlyInAnyOrder(Handled.APPLIED, Handled.DUPLICATE, Handled.DUPLICATE);
            assertThat(u2.applyCalls.get() - before).isEqualTo(1);
        }
    }

    @Test
    void whenU2RejectsTheEventIsRecordedAsIgnoredAndThePaymentBecomesIgnored() {
        u2.script(new ApplyReply(ApplyResult.REJECTED, "CANCELLED"));

        Handled handled = service.handleWebhook(call("e1", Outcome.APPROVED));

        assertThat(handled).isEqualTo(Handled.IGNORED);
        assertThat(store.findEvent("e1").orElseThrow().handled()).isEqualTo(Handled.IGNORED);
        assertThat(status()).isEqualTo(PaymentStatus.IGNORED);
    }

    @Test
    void aNewEventForAnAlreadyEndedPaymentIsForwardedButTheStatusStays() {
        service.handleWebhook(call("e1", Outcome.APPROVED));
        u2.script(new ApplyReply(ApplyResult.REJECTED, "CONFIRMED"));

        Handled rejected = service.handleWebhook(call("e2", Outcome.FAILED));

        assertThat(rejected).isEqualTo(Handled.IGNORED);
        assertThat(u2.applyCalls.get()).isEqualTo(2);
        assertThat(status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(store.findEvent("e2").orElseThrow().handled()).isEqualTo(Handled.IGNORED);
    }

    @Test
    void anAppliedResultForAnAlreadyEndedPaymentStillLeavesThePaymentUnchanged() {
        u2.script(new ApplyReply(ApplyResult.REJECTED, "CANCELLED"));
        service.handleWebhook(call("e1", Outcome.APPROVED));
        assertThat(status()).isEqualTo(PaymentStatus.IGNORED);

        Handled handled = service.handleWebhook(call("e2", Outcome.FAILED));

        assertThat(handled).isEqualTo(Handled.APPLIED);
        assertThat(status()).isEqualTo(PaymentStatus.IGNORED);
        assertThat(store.findEvent("e2").orElseThrow().handled()).isEqualTo(Handled.APPLIED);
    }

    @Test
    void whenU2FailsTheAnswerIs502NothingIsRecordedAndTheSameEventIsProcessedFromTheStartOnResend() {
        u2.script(new PaymentException(ErrorCode.UPSTREAM_FAILED));

        PaymentException error = errorOf(() -> service.handleWebhook(call("e1", Outcome.APPROVED)));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
        assertThat(store.findEvent("e1")).isEmpty();
        assertThat(status()).isEqualTo(PaymentStatus.REQUESTED);

        Handled resent = service.handleWebhook(call("e1", Outcome.APPROVED));

        assertThat(resent).isEqualTo(Handled.APPLIED);
        assertThat(u2.applyCalls.get()).isEqualTo(2);
        assertThat(status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void anUnexpectedU2ExceptionIsAlsoAnUpstreamFailureWithNothingRecorded() {
        u2.script(new IllegalStateException("boom"));

        PaymentException error = errorOf(() -> service.handleWebhook(call("e1", Outcome.APPROVED)));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
        assertThat(store.findEvent("e1")).isEmpty();
        assertThat(status()).isEqualTo(PaymentStatus.REQUESTED);
    }

    @Test
    void twoDifferentEventsForTheSamePaymentAtOnceAreSerializedAndThePaymentFollowsU2sFirstVerdict()
            throws Exception {
        u2.applyDelay = Duration.ofMillis(120);
        // u2는 먼저 도착한 쪽만 적용하고 다음은 거부한다.
        u2.script(new ApplyReply(ApplyResult.APPLIED, "CONFIRMED"));
        u2.script(new ApplyReply(ApplyResult.REJECTED, "CONFIRMED"));
        CountDownLatch start = new CountDownLatch(1);
        Future<Handled> approved = pool.submit(() -> {
            start.await();
            return service.handleWebhook(call("eA", Outcome.APPROVED));
        });
        Future<Handled> failed = pool.submit(() -> {
            start.await();
            return service.handleWebhook(call("eB", Outcome.FAILED));
        });
        start.countDown();

        List<Handled> results = List.of(approved.get(5, TimeUnit.SECONDS), failed.get(5, TimeUnit.SECONDS));

        assertThat(results).containsExactlyInAnyOrder(Handled.APPLIED, Handled.IGNORED);
        assertThat(u2.maxApplyInFlight.get()).isEqualTo(1);
        String appliedEvent = store.findEvent("eA").orElseThrow().handled() == Handled.APPLIED ? "eA" : "eB";
        Outcome appliedOutcome = store.findEvent(appliedEvent).orElseThrow().outcome();
        assertThat(status()).isEqualTo(PaymentStatus.of(appliedOutcome));
    }

    @Test
    void eventsForDifferentPaymentsDoNotBlockEachOther() throws Exception {
        PaymentReceipt other = service.request(new PaymentCommand(SESSION, "pass-2", 2, "key-2", "APPROVE", 0),
                System.nanoTime());
        u2.applyDelay = Duration.ofMillis(300);
        long start = System.nanoTime();
        Future<Handled> a = pool.submit(() -> service.handleWebhook(call("eA", Outcome.APPROVED)));
        Future<Handled> b = pool.submit(() -> service.handleWebhook(
                new WebhookCall("eB", other.paymentId(), Outcome.APPROVED, clock.instant())));

        a.get(5, TimeUnit.SECONDS);
        b.get(5, TimeUnit.SECONDS);

        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(550L);
        assertThat(u2.maxApplyInFlight.get()).isEqualTo(2);
    }

    @Test
    void anUnknownPaymentIdIs404AndU2IsNotCalled() {
        PaymentException error = errorOf(() -> service.handleWebhook(
                new WebhookCall("e1", "no-such-payment", Outcome.APPROVED, clock.instant())));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.UNKNOWN_PAYMENT);
        assertThat(u2.applyCalls.get()).isZero();
        assertThat(store.findEvent("e1")).isEmpty();
    }

    @Test
    void anEventLockWaitBeyondTheLimitIs502AndNothingIsRecorded() {
        PaymentService impatient = new PaymentService(store, u2, gateway, clock, Duration.ofMillis(100));
        Lease held = store.tryLock(LockScope.EVENT, "e1", Duration.ZERO).orElseThrow();

        PaymentException error = errorOf(() -> impatient.handleWebhook(call("e1", Outcome.APPROVED)));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
        assertThat(u2.applyCalls.get()).isZero();
        assertThat(store.findEvent("e1")).isEmpty();
        held.close();
    }

    @Test
    void aPaymentLockWaitBeyondTheLimitIs502AndTheEventLockIsReleased() {
        PaymentService impatient = new PaymentService(store, u2, gateway, clock, Duration.ofMillis(100));
        Lease held = store.tryLock(LockScope.PAYMENT, payment.paymentId(), Duration.ZERO).orElseThrow();

        PaymentException error = errorOf(() -> impatient.handleWebhook(call("e1", Outcome.APPROVED)));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
        assertThat(u2.applyCalls.get()).isZero();
        assertThat(status()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(store.tryLock(LockScope.EVENT, "e1", Duration.ZERO)).isPresent();
        held.close();
    }

    @Test
    void theDefaultLockWaitLimitIsSevenSeconds() {
        assertThat(PaymentService.DEFAULT_LOCK_WAIT).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void theU2CallIsMadeWhileThePaymentLockIsHeld() {
        u2.applyDelay = Duration.ofMillis(300);
        List<Boolean> lockWasHeld = Collections.synchronizedList(new ArrayList<>());
        Future<Handled> running = pool.submit(() -> service.handleWebhook(call("e1", Outcome.APPROVED)));
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        lockWasHeld.add(store.tryLock(LockScope.PAYMENT, payment.paymentId(), Duration.ZERO).isEmpty());

        try {
            running.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertThat(lockWasHeld).containsExactly(true);
    }
}

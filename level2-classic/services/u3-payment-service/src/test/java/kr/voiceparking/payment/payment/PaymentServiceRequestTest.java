package kr.voiceparking.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import kr.voiceparking.payment.payment.PaymentStore.Lease;
import kr.voiceparking.payment.payment.PaymentStore.LockScope;
import kr.voiceparking.payment.support.FakeReservationClient;
import kr.voiceparking.payment.support.RecordingGateway;
import kr.voiceparking.payment.support.TestClock;

/** 결제 요청 처리(BR1.1~BR1.5, BR4.1, NFR5.4, NFR5.5, E2, E8). u2는 가짜, 모의 PG는 기록만 하는 가짜다. */
class PaymentServiceRequestTest {

    private static final String SESSION = "11111111-2222-3333-4444-555555555555";

    private final PaymentStore store = new PaymentStore();
    private final FakeReservationClient u2 = new FakeReservationClient();
    private final RecordingGateway gateway = new RecordingGateway();
    private final TestClock clock = new TestClock(Instant.parse("2026-11-05T01:00:00Z"));
    private final PaymentService service = new PaymentService(store, u2, gateway, clock, Duration.ofSeconds(7));
    private final ExecutorService pool = Executors.newCachedThreadPool();

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    private static PaymentCommand command(String key) {
        return new PaymentCommand(SESSION, "pass-1", 2, key, "APPROVE", 0);
    }

    private PaymentReceipt request(PaymentCommand command) {
        return service.request(command, System.nanoTime());
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
    void aNewPaymentCallsU2OnceStoresRequestedAndRegistersWithThePgOnce() {
        u2.amount = 5500;

        PaymentReceipt receipt = request(new PaymentCommand(SESSION, "pass-9", 3, "key-1", "DELAY", 30));

        assertThat(u2.createCalls.get()).isEqualTo(1);
        assertThat(u2.creates().get(0)).isEqualTo(new CreateCall(SESSION, "pass-9", 3, "key-1"));
        Payment payment = store.findByKey(SESSION, "key-1").orElseThrow();
        assertThat(payment.status()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.amount()).isEqualTo(5500);
        assertThat(payment.requestedAt()).isEqualTo(FakeReservationClient.REQUESTED_AT);
        assertThat(payment.reservationId()).isEqualTo(receipt.reservationId());
        assertThat(gateway.registered()).containsExactly(new PgPayment(payment.paymentId(), MockScenario.DELAY, 30));
        assertThat(receipt.paymentId()).isEqualTo(payment.paymentId());
        assertThat(receipt.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(receipt.accepted()).isTrue();
        assertThat(receipt.paymentRequestedAt()).isEqualTo(FakeReservationClient.REQUESTED_AT);
    }

    @Test
    void theScenarioAndDelayDefaultToApproveAndZero() {
        request(new PaymentCommand(SESSION, "pass-1", 1, "key-1", null, null));

        assertThat(gateway.registered().get(0).mockScenario()).isEqualTo(MockScenario.APPROVE);
        assertThat(gateway.registered().get(0).mockDelaySeconds()).isZero();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "NULL, pass-1, 2, key-1, APPROVE, 0, MISSING_SESSION",
            "not-a-uuid, pass-1, 2, key-1, APPROVE, 0, MISSING_SESSION",
            "11111111-2222-3333-4444-555555555555, NULL, 2, key-1, APPROVE, 0, INVALID_REQUEST",
            "11111111-2222-3333-4444-555555555555, ' ', 2, key-1, APPROVE, 0, INVALID_REQUEST",
            "11111111-2222-3333-4444-555555555555, pass-1, NULL, key-1, APPROVE, 0, INVALID_REQUEST",
            "11111111-2222-3333-4444-555555555555, pass-1, 0, key-1, APPROVE, 0, INVALID_REQUEST",
            "11111111-2222-3333-4444-555555555555, pass-1, 25, key-1, APPROVE, 0, INVALID_REQUEST",
            "11111111-2222-3333-4444-555555555555, pass-1, 2, NULL, APPROVE, 0, INVALID_REQUEST",
            "11111111-2222-3333-4444-555555555555, pass-1, 2, ' ', APPROVE, 0, INVALID_REQUEST",
            "11111111-2222-3333-4444-555555555555, pass-1, 2, key-1, BOGUS, 0, INVALID_REQUEST",
            "11111111-2222-3333-4444-555555555555, pass-1, 2, key-1, APPROVE, -1, INVALID_REQUEST",
            "11111111-2222-3333-4444-555555555555, pass-1, 2, key-1, DELAY, 601, INVALID_REQUEST" })
    void malformedRequestsAreRejectedBeforeAnythingIsCalledOrCreated(String session, String passId,
            Integer hours, String key, String scenario, Integer delay, ErrorCode expected) {
        PaymentException error = errorOf(() -> request(new PaymentCommand(session, passId, hours, key, scenario,
                delay)));

        assertThat(error.errorCode()).isEqualTo(expected);
        assertThat(u2.createCalls.get()).isZero();
        assertThat(gateway.count()).isZero();
    }

    @Test
    void overlongPassIdAndKeyAreRejectedAndTheBoundaryLengthIsAccepted() {
        String max = "x".repeat(100);
        String tooLong = "x".repeat(101);

        assertThat(errorOf(() -> request(new PaymentCommand(SESSION, tooLong, 2, "k", "APPROVE", 0))).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(errorOf(() -> request(new PaymentCommand(SESSION, "p", 2, tooLong, "APPROVE", 0))).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(request(new PaymentCommand(SESSION, max, 24, max, "APPROVE", 600)).paymentId()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({ "PASS_NOT_FOUND", "DURATION_NOT_AVAILABLE" })
    void u2RejectionsAreRelayedWithTheSameCodeAndNothingIsCreated(ErrorCode code) {
        u2.createFailure = new PaymentException(code);

        PaymentException error = errorOf(() -> request(command("key-1")));

        assertThat(error.errorCode()).isEqualTo(code);
        assertThat(store.findByKey(SESSION, "key-1")).isEmpty();
        assertThat(gateway.count()).isZero();
    }

    @Test
    void aPendingExistsRejectionKeepsTheExistingReservationId() {
        u2.createFailure = new PaymentException(ErrorCode.PENDING_EXISTS, "res-existing");

        PaymentException error = errorOf(() -> request(command("key-1")));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.PENDING_EXISTS);
        assertThat(error.reservationId()).isEqualTo("res-existing");
        assertThat(store.findByKey(SESSION, "key-1")).isEmpty();
        assertThat(gateway.count()).isZero();
    }

    @Test
    void anyOtherU2FailureBecomesUpstreamFailedWithoutAPayment() {
        for (RuntimeException failure : List.of(new PaymentException(ErrorCode.UPSTREAM_FAILED),
                new PaymentException(ErrorCode.INVALID_REQUEST), new IllegalStateException("boom"))) {
            u2.createFailure = failure;

            assertThat(errorOf(() -> request(command("key-1"))).errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
        }
        assertThat(store.findByKey(SESSION, "key-1")).isEmpty();
        assertThat(gateway.count()).isZero();
    }

    @Test
    void aRepeatedKeyReturnsTheSamePaymentWithoutANewPgRegistration() {
        PaymentReceipt first = request(command("key-1"));

        PaymentReceipt second = request(command("key-1"));

        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(second.reservationId()).isEqualTo(first.reservationId());
        assertThat(second.accepted()).isTrue();
        assertThat(u2.createCalls.get()).isEqualTo(2);
        assertThat(gateway.count()).isEqualTo(1);
    }

    @Test
    void aRepeatedKeyAfterTheReservationEndedReturnsTheCurrentStatusAndDoesNotCallThePgAgain() {
        PaymentReceipt first = request(command("key-1"));
        u2.currentStatus = "CONFIRMED";

        PaymentReceipt second = request(command("key-1"));

        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(second.status()).isEqualTo("CONFIRMED");
        assertThat(second.accepted()).isFalse();
        assertThat(gateway.count()).isEqualTo(1);
    }

    @Test
    void aRepeatedKeyWithOtherPassAndDurationStillReturnsTheExistingResult() {
        PaymentReceipt first = request(command("key-1"));

        PaymentReceipt second = request(new PaymentCommand(SESSION, "other-pass", 9, "key-1", "FAIL", 5));

        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(store.findByKey(SESSION, "key-1").orElseThrow().passId()).isEqualTo("pass-1");
        assertThat(gateway.count()).isEqualTo(1);
    }

    @Test
    void aPaymentWithoutAPgRequestIsRegisteredWhenTheReservationIsStillPending() {
        PaymentReceipt first = request(command("key-1"));
        // 이전 호출이 PG 등록 전에 끝난 상황을 만든다: 같은 Payment를 PgRequest 기록 없이 새 저장소에 넣는다.
        PaymentStore fresh = new PaymentStore();
        RecordingGateway freshGateway = new RecordingGateway();
        PaymentService other = new PaymentService(fresh, u2, freshGateway, clock, Duration.ofSeconds(7));
        fresh.save(store.findById(first.paymentId()).orElseThrow());

        PaymentReceipt replay = other.request(command("key-1"), System.nanoTime());

        assertThat(replay.paymentId()).isEqualTo(first.paymentId());
        assertThat(freshGateway.registered()).hasSize(1);
        assertThat(fresh.hasPgRequest(first.paymentId())).isTrue();
    }

    @Test
    void aPaymentWithoutAPgRequestIsNotRegisteredWhenTheReservationAlreadyEnded() {
        PaymentReceipt first = request(command("key-1"));
        PaymentStore fresh = new PaymentStore();
        RecordingGateway freshGateway = new RecordingGateway();
        PaymentService other = new PaymentService(fresh, u2, freshGateway, clock, Duration.ofSeconds(7));
        fresh.save(store.findById(first.paymentId()).orElseThrow());
        u2.currentStatus = "CANCELLED";

        PaymentReceipt replay = other.request(command("key-1"), System.nanoTime());

        assertThat(replay.status()).isEqualTo("CANCELLED");
        assertThat(freshGateway.count()).isZero();
    }

    @Test
    void whenU2HasTheReservationButThisServiceHasNoPaymentItIsCreatedAndRegisteredIfPending() {
        // 앞선 호출이 u2에서 예약을 만든 직후 실패한 상황: u2에는 예약이 있고 이 저장소에는 Payment가 없다.
        PaymentStore empty = new PaymentStore();
        RecordingGateway g1 = new RecordingGateway();
        new PaymentService(empty, u2, g1, clock, Duration.ofSeconds(7)).request(command("key-1"), System.nanoTime());
        PaymentStore lost = new PaymentStore();
        RecordingGateway g2 = new RecordingGateway();
        PaymentService service2 = new PaymentService(lost, u2, g2, clock, Duration.ofSeconds(7));

        PaymentReceipt receipt = service2.request(command("key-1"), System.nanoTime());

        assertThat(lost.findByKey(SESSION, "key-1")).isPresent();
        assertThat(lost.findByKey(SESSION, "key-1").orElseThrow().status()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(receipt.accepted()).isTrue();
        assertThat(g2.count()).isEqualTo(1);
    }

    @Test
    void whenU2HasAnEndedReservationButNoLocalPaymentOneIsCreatedWithoutAPgRegistration() {
        request(command("key-1"));
        PaymentStore lost = new PaymentStore();
        RecordingGateway g2 = new RecordingGateway();
        PaymentService service2 = new PaymentService(lost, u2, g2, clock, Duration.ofSeconds(7));
        u2.currentStatus = "PAYMENT_FAILED";

        PaymentReceipt receipt = service2.request(command("key-1"), System.nanoTime());

        assertThat(lost.findByKey(SESSION, "key-1")).isPresent();
        assertThat(receipt.status()).isEqualTo("PAYMENT_FAILED");
        assertThat(g2.count()).isZero();
    }

    @Test
    void threeConcurrentRequestsWithTheSameKeyMakeOnePaymentAndOnePgRegistration() throws Exception {
        u2.createDelay = Duration.ofMillis(80);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentReceipt>> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return request(command("key-1"));
            }));
        }
        start.countDown();

        List<String> ids = new ArrayList<>();
        for (Future<PaymentReceipt> result : results) {
            ids.add(result.get(5, TimeUnit.SECONDS).paymentId());
        }

        assertThat(ids).containsOnly(ids.get(0));
        assertThat(gateway.count()).isEqualTo(1);
        assertThat(store.findByKey(SESSION, "key-1")).isPresent();
    }

    @Test
    void differentKeysDoNotWaitForEachOther() throws Exception {
        u2.createDelay = Duration.ofMillis(300);
        long start = System.nanoTime();
        Future<PaymentReceipt> a = pool.submit(() -> service.request(
                new PaymentCommand("aaaaaaaa-2222-3333-4444-555555555555", "p", 1, "k", "APPROVE", 0),
                System.nanoTime()));
        Future<PaymentReceipt> b = pool.submit(() -> service.request(
                new PaymentCommand("bbbbbbbb-2222-3333-4444-555555555555", "p", 1, "k", "APPROVE", 0),
                System.nanoTime()));

        a.get(5, TimeUnit.SECONDS);
        b.get(5, TimeUnit.SECONDS);

        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(550L);
    }

    @Test
    void whenTheFirstU2CallNeverAnswersBothRequestsEndWith502WithinTheDeadlineFromReceipt() throws Exception {
        u2.hangOnCreate = true;
        List<Long> elapsedMs = Collections.synchronizedList(new ArrayList<>());
        List<ErrorCode> codes = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            int index = i;
            pool.submit(() -> {
                try {
                    Thread.sleep(index * 100L);
                    long received = System.nanoTime();
                    try {
                        request(command("key-1"));
                    } catch (PaymentException e) {
                        codes.add(e.errorCode());
                    }
                    elapsedMs.add((System.nanoTime() - received) / 1_000_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(codes).containsExactly(ErrorCode.UPSTREAM_FAILED, ErrorCode.UPSTREAM_FAILED);
        assertThat(elapsedMs).allSatisfy(ms -> assertThat(ms).isLessThanOrEqualTo(1700L));
        assertThat(store.findByKey(SESSION, "key-1")).isEmpty();
        assertThat(gateway.count()).isZero();
    }

    @Test
    void theU2CallGetsOnlyTheTimeLeftOfTheOneAndAHalfSecondDeadline() {
        long receivedThreeHundredMsAgo = System.nanoTime() - Duration.ofMillis(300).toNanos();

        service.request(command("key-1"), receivedThreeHundredMsAgo);

        assertThat(u2.lastCreateTimeout).isBetween(Duration.ofMillis(900), Duration.ofMillis(1200));
    }

    @Test
    void whenLessThan200msIsLeftU2IsNotCalledAndTheAnswerIs502() {
        long receivedLongAgo = System.nanoTime() - Duration.ofMillis(1400).toNanos();

        PaymentException error = errorOf(() -> service.request(command("key-1"), receivedLongAgo));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
        assertThat(u2.createCalls.get()).isZero();
        assertThat(store.findByKey(SESSION, "key-1")).isEmpty();
    }

    @Test
    void aKeyLockHeldElsewhereMakesTheRequestGiveUpWith502WithinTheDeadline() {
        Lease held = store.tryLock(LockScope.KEY, PaymentStore.keyLockId(SESSION, "key-1"), Duration.ZERO)
                .orElseThrow();
        long receivedAlmostAtTheDeadline = System.nanoTime() - Duration.ofMillis(1350).toNanos();
        long start = System.nanoTime();

        PaymentException error = errorOf(() -> service.request(command("key-1"), receivedAlmostAtTheDeadline));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(400L);
        assertThat(u2.createCalls.get()).isZero();
        held.close();
    }

    @Test
    void aNewKeyIsAllowedAfterThePreviousPaymentFailed() {
        PaymentReceipt first = request(command("key-1"));
        store.save(store.findById(first.paymentId()).orElseThrow().settle(PaymentStatus.FAILED));

        PaymentReceipt second = request(command("key-2"));

        assertThat(second.paymentId()).isNotEqualTo(first.paymentId());
        assertThat(second.reservationId()).isNotEqualTo(first.reservationId());
        assertThat(gateway.count()).isEqualTo(2);
    }

    @Test
    void theClientAmountIsNeverUsed() {
        // 금액은 u2 응답 값만 쓴다: 명령에는 금액 필드가 없고 Payment는 u2의 값을 담는다.
        u2.amount = 123;

        request(command("key-1"));

        assertThat(store.findByKey(SESSION, "key-1").orElseThrow().amount()).isEqualTo(123);
        assertThatThrownBy(() -> PaymentCommand.class.getDeclaredField("amount")).isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    void sessionsAreIndependentForTheSameKey() {
        PaymentReceipt a = request(new PaymentCommand(UUID.randomUUID().toString(), "p", 1, "same", "APPROVE", 0));
        PaymentReceipt b = request(new PaymentCommand(UUID.randomUUID().toString(), "p", 1, "same", "APPROVE", 0));

        assertThat(a.paymentId()).isNotEqualTo(b.paymentId());
        assertThat(gateway.count()).isEqualTo(2);
    }
}

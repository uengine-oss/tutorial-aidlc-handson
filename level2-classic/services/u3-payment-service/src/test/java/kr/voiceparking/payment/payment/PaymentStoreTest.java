package kr.voiceparking.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import kr.voiceparking.payment.payment.PaymentStore.Lease;
import kr.voiceparking.payment.payment.PaymentStore.LockScope;

/** 저장소: 조회, 이벤트 기록, 잠금(BR1.3, BR3.4, BR5.1, NFR4.2). */
class PaymentStoreTest {

    private static final Instant AT = Instant.parse("2026-11-05T01:00:00Z");

    private final PaymentStore store = new PaymentStore();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    private static Payment payment(String session, String key) {
        return Payment.requested("res-" + key, session, key, "pass-1", 2, 4000, MockScenario.APPROVE, 0, AT);
    }

    @Test
    void aSavedPaymentIsFoundByIdAndBySessionAndKey() {
        Payment payment = payment("s1", "k1");

        store.save(payment);

        assertThat(store.findById(payment.paymentId())).contains(payment);
        assertThat(store.findByKey("s1", "k1")).contains(payment);
    }

    @Test
    void unknownIdsAndKeysAreEmpty() {
        store.save(payment("s1", "k1"));

        assertThat(store.findById("nope")).isEmpty();
        assertThat(store.findByKey("s1", "other")).isEmpty();
        assertThat(store.findByKey("s2", "k1")).isEmpty();
    }

    @Test
    void savingAgainReplacesTheValueForTheSamePayment() {
        Payment payment = payment("s1", "k1");
        store.save(payment);

        store.save(payment.settle(PaymentStatus.APPROVED));

        assertThat(store.findById(payment.paymentId())).map(Payment::status).contains(PaymentStatus.APPROVED);
        assertThat(store.findByKey("s1", "k1")).map(Payment::status).contains(PaymentStatus.APPROVED);
    }

    @Test
    void aDifferentPaymentForTheSameSessionAndKeyIsRejected() {
        store.save(payment("s1", "k1"));

        assertThatThrownBy(() -> store.save(payment("s1", "k1"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eventsAreRecordedOnceAndTheFirstRecordWins() {
        WebhookEvent first = new WebhookEvent("e1", "p1", Outcome.APPROVED, AT, Handled.APPLIED);
        WebhookEvent again = new WebhookEvent("e1", "p1", Outcome.FAILED, AT, Handled.IGNORED);

        assertThat(store.findEvent("e1")).isEmpty();
        assertThat(store.recordEvent(first)).isTrue();
        assertThat(store.recordEvent(again)).isFalse();

        assertThat(store.findEvent("e1")).contains(first);
    }

    @Test
    void aPaymentIsRegisteredWithThePgOnlyOnce() {
        Payment payment = payment("s1", "k1");
        store.save(payment);

        assertThat(store.hasPgRequest(payment.paymentId())).isFalse();
        store.markPgRequested(payment.paymentId());
        assertThat(store.hasPgRequest(payment.paymentId())).isTrue();
    }

    @Test
    void locksOnDifferentIdsDoNotBlockEachOther() {
        try (Lease a = store.tryLock(LockScope.KEY, "a", Duration.ZERO).orElseThrow();
                Lease b = store.tryLock(LockScope.KEY, "b", Duration.ZERO).orElseThrow();
                Lease c = store.tryLock(LockScope.EVENT, "a", Duration.ZERO).orElseThrow();
                Lease d = store.tryLock(LockScope.PAYMENT, "a", Duration.ZERO).orElseThrow()) {
            assertThat(List.of(a, b, c, d)).hasSize(4);
        }
    }

    @Test
    void theSameLockIsHeldByOneCallerAtATimeEvenWithinOneThread() {
        Lease held = store.tryLock(LockScope.PAYMENT, "p1", Duration.ZERO).orElseThrow();

        Optional<Lease> second = store.tryLock(LockScope.PAYMENT, "p1", Duration.ZERO);
        assertThat(second).isEmpty();

        held.close();
        assertThat(store.tryLock(LockScope.PAYMENT, "p1", Duration.ZERO)).isPresent();
    }

    @Test
    void waitersGetTheSameLockOneAfterAnotherInOrder() throws Exception {
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(3);
        Lease first = store.tryLock(LockScope.KEY, "k", Duration.ZERO).orElseThrow();
        for (int i = 1; i <= 3; i++) {
            int id = i;
            pool.submit(() -> {
                try (Lease lease = store.tryLock(LockScope.KEY, "k", Duration.ofSeconds(5)).orElseThrow()) {
                    order.add(id);
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            Thread.sleep(50);
        }

        first.close();

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(order).containsExactly(1, 2, 3);
    }

    @Test
    void tryLockGivesUpAfterTheWaitLimit() {
        Lease held = store.tryLock(LockScope.EVENT, "e", Duration.ZERO).orElseThrow();

        long start = System.nanoTime();
        Optional<Lease> result = store.tryLock(LockScope.EVENT, "e", Duration.ofMillis(150));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(result).isEmpty();
        assertThat(elapsedMs).isBetween(140L, 1000L);
        held.close();
    }

    @Test
    void closingALeaseTwiceReleasesItOnlyOnce() {
        Lease lease = store.tryLock(LockScope.KEY, "x", Duration.ZERO).orElseThrow();
        lease.close();
        Lease next = store.tryLock(LockScope.KEY, "x", Duration.ZERO).orElseThrow();

        lease.close();

        assertThat(store.tryLock(LockScope.KEY, "x", Duration.ZERO)).isEmpty();
        next.close();
    }

    @Test
    void sessionAndKeyCombineIntoAnUnambiguousLockId() {
        assertThat(PaymentStore.keyLockId("ab", "c")).isNotEqualTo(PaymentStore.keyLockId("a", "bc"));
    }
}

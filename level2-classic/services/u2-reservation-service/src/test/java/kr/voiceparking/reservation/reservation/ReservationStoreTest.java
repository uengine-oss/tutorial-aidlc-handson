package kr.voiceparking.reservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import kr.voiceparking.reservation.domain.AppliedEvent;
import kr.voiceparking.reservation.domain.ApplyResult;
import kr.voiceparking.reservation.domain.Outcome;
import kr.voiceparking.reservation.domain.Reservation;
import kr.voiceparking.reservation.domain.ReservationStatus;

/** 저장소: 조회 색인, 판정 기록, 서로 다른 키의 잠금이 막지 않음 (BR1.2, BR1.3, BR3.3, BR5.2, NFR4.2). */
class ReservationStoreTest {

    private static final Instant T0 = Instant.parse("2026-11-07T00:00:00Z");

    private final ReservationStore store = new ReservationStore();

    private static Reservation reservation(String id, String session, String key, Instant at) {
        return Reservation.create(id, session, key, "pass-1", "강남 주차장", 2, 6000, at);
    }

    @Test
    void findsByReservationId() {
        store.save(reservation("r-1", "s-1", "k-1", T0));

        assertThat(store.findById("r-1")).isPresent();
        assertThat(store.findById("nope")).isEmpty();
    }

    @Test
    void findsBySessionAndIdempotencyKey() {
        store.save(reservation("r-1", "s-1", "k-1", T0));

        assertThat(store.findByKey("s-1", "k-1")).get().extracting(Reservation::reservationId).isEqualTo("r-1");
        assertThat(store.findByKey("s-2", "k-1")).isEmpty();
        assertThat(store.findByKey("s-1", "k-2")).isEmpty();
    }

    @Test
    void findsPendingReservationOfSessionOnly() {
        Reservation first = reservation("r-1", "s-1", "k-1", T0);
        store.save(first);
        store.save(reservation("r-2", "s-2", "k-2", T0));

        assertThat(store.findPending("s-1")).get().extracting(Reservation::reservationId).isEqualTo("r-1");

        store.save(first.paymentFailed(T0.plusSeconds(5)));
        assertThat(store.findPending("s-1")).isEmpty();
        assertThat(store.findPending("s-3")).isEmpty();
    }

    @Test
    void savingReplacesTheStoredValue() {
        Reservation first = reservation("r-1", "s-1", "k-1", T0);
        store.save(first);
        store.save(first.confirm(T0.plusSeconds(3)));

        assertThat(store.findById("r-1")).get().extracting(Reservation::status).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(store.findByKey("s-1", "k-1")).get().extracting(Reservation::version).isEqualTo(2);
    }

    @Test
    void latestIsTheLatestPaymentRequestAndTheLaterSavedOnTies() {
        store.save(reservation("r-1", "s-1", "k-1", T0));
        store.save(reservation("r-2", "s-1", "k-2", T0.plusSeconds(10)));
        store.save(reservation("r-3", "s-1", "k-3", T0.plusSeconds(10)));
        store.save(reservation("r-4", "s-1", "k-4", T0.plusSeconds(5)));

        assertThat(store.findLatest("s-1")).get().extracting(Reservation::reservationId).isEqualTo("r-3");
        assertThat(store.findLatest("s-none")).isEmpty();
    }

    @Test
    void recordsAndFindsAppliedEventsPerReservation() {
        AppliedEvent event = new AppliedEvent("e-1", Outcome.APPROVED, ApplyResult.APPLIED,
                ReservationStatus.CONFIRMED, T0);
        store.recordEvent("r-1", event);

        assertThat(store.findEvent("r-1", "e-1")).contains(event);
        assertThat(store.findEvent("r-1", "e-2")).isEmpty();
        assertThat(store.findEvent("r-2", "e-1")).isEmpty();
    }

    @Test
    void recordingTheSameEventIdKeepsTheFirstDecision() {
        AppliedEvent first = new AppliedEvent("e-1", Outcome.APPROVED, ApplyResult.APPLIED,
                ReservationStatus.CONFIRMED, T0);
        AppliedEvent second = new AppliedEvent("e-1", Outcome.APPROVED, ApplyResult.REJECTED,
                ReservationStatus.CONFIRMED, T0.plusSeconds(1));
        store.recordEvent("r-1", first);
        store.recordEvent("r-1", second);

        assertThat(store.findEvent("r-1", "e-1")).contains(first);
    }

    @Test
    void locksOfDifferentKeysDoNotBlockEachOther() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> store.withReservationLock("r-1", () -> {
            holding.countDown();
            await(release);
            return null;
        }));
        holder.start();
        assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean otherReservationRan = new AtomicBoolean();
        AtomicBoolean otherSessionRan = new AtomicBoolean();
        Thread other = new Thread(() -> {
            store.withReservationLock("r-2", () -> otherReservationRan.getAndSet(true));
            store.withSessionLock("s-1", () -> otherSessionRan.getAndSet(true));
        });
        other.start();
        other.join(2000);

        assertThat(otherReservationRan).isTrue();
        assertThat(otherSessionRan).isTrue();
        release.countDown();
        holder.join(2000);
    }

    @Test
    void sameKeyLockSerializesWork() throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> store.withReservationLock("r-1", () -> {
            holding.countDown();
            await(release);
            return null;
        }));
        holder.start();
        assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean ran = new AtomicBoolean();
        Thread waiter = new Thread(() -> store.withReservationLock("r-1", () -> ran.getAndSet(true)));
        waiter.start();
        waiter.join(200);
        assertThat(ran).isFalse();

        release.countDown();
        waiter.join(2000);
        assertThat(ran).isTrue();
        holder.join(2000);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

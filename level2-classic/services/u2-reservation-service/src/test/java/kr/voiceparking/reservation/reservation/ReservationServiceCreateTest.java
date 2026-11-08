package kr.voiceparking.reservation.reservation;

import static kr.voiceparking.reservation.support.ServiceFixture.OTHER_SESSION;
import static kr.voiceparking.reservation.support.ServiceFixture.SESSION;
import static kr.voiceparking.reservation.support.ServiceFixture.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import kr.voiceparking.reservation.domain.Outcome;
import kr.voiceparking.reservation.domain.Reservation;
import kr.voiceparking.reservation.domain.ReservationStatus;
import kr.voiceparking.reservation.reservation.ReservationService.CreateResult;
import kr.voiceparking.reservation.support.ServiceFixture;

/** 예약 생성 W1: 검증, 멱등 키, 결제 대기 1건 제한, u1 결과 전달 (BR1.1~BR1.5, BR2.6, BR5.2). */
class ReservationServiceCreateTest {

    private final ServiceFixture fx = new ServiceFixture();

    private CreateCommand command(String key) {
        return new CreateCommand(SESSION, "pass-1", 2, key);
    }

    private static void assertCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ReservationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(expected));
    }

    @Test
    void createsPendingReservationWithAmountAndLotFromU1() {
        CreateResult result = fx.service.create(command("key-1"));

        Reservation r = result.reservation();
        assertThat(result.created()).isTrue();
        assertThat(r.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(r.version()).isEqualTo(1);
        assertThat(r.amount()).isEqualTo(6000);
        assertThat(r.lotName()).isEqualTo("강남 주차장");
        assertThat(r.durationHours()).isEqualTo(2);
        assertThat(r.paymentRequestedAt()).isEqualTo(T0);
        assertThat(r.reservationId()).isNotBlank();
        assertThat(fx.store.findById(r.reservationId())).isPresent();
        // 첫 스냅샷(version 1)을 푸시하고 마감 시각에 타이머를 하나 예약한다.
        assertThat(fx.sink.count()).isEqualTo(1);
        assertThat(fx.sink.last().version()).isEqualTo(1);
        assertThat(fx.scheduler.only().at()).isEqualTo(T0.plus(fx.timeout()));
    }

    static List<CreateCommand> invalidCommands() {
        String tooLong = "x".repeat(101);
        return List.of(
                new CreateCommand("not-a-uuid", "pass-1", 2, "k"),
                new CreateCommand(null, "pass-1", 2, "k"),
                new CreateCommand(SESSION, " ", 2, "k"),
                new CreateCommand(SESSION, null, 2, "k"),
                new CreateCommand(SESSION, "pass-1", 2, ""),
                new CreateCommand(SESSION, "pass-1", 2, null),
                new CreateCommand(SESSION, "pass-1", 0, "k"),
                new CreateCommand(SESSION, "pass-1", 25, "k"),
                new CreateCommand(SESSION, "pass-1", null, "k"),
                new CreateCommand(SESSION, tooLong, 2, "k"),
                new CreateCommand(SESSION, "pass-1", 2, tooLong));
    }

    @ParameterizedTest
    @MethodSource("invalidCommands")
    void rejectsMalformedRequestsWithoutCallingU1OrCreatingAnything(CreateCommand invalid) {
        assertCode(() -> fx.service.create(invalid), ErrorCode.INVALID_REQUEST);

        assertThat(fx.searchCalls.get()).isZero();
        assertThat(fx.sink.count()).isZero();
        assertThat(fx.scheduler.scheduled()).isEmpty();
    }

    @Test
    void acceptsBoundaryDurationsAndLengths() {
        assertThat(fx.service.create(new CreateCommand(SESSION, "p".repeat(100), 1, "a".repeat(100))).created())
                .isTrue();
        assertThat(fx.service.create(new CreateCommand(OTHER_SESSION, "pass-1", 24, "k")).created()).isTrue();
    }

    @Test
    void sameKeyReturnsTheSameReservationWithoutNewTimerOrPush() {
        CreateResult first = fx.service.create(command("key-1"));
        CreateResult second = fx.service.create(command("key-1"));

        assertThat(second.created()).isFalse();
        assertThat(second.reservation().reservationId()).isEqualTo(first.reservation().reservationId());
        assertThat(fx.searchCalls.get()).isEqualTo(1);
        assertThat(fx.sink.count()).isEqualTo(1);
        assertThat(fx.scheduler.scheduled()).hasSize(1);
    }

    @Test
    void sameKeyReturnsTheCurrentStatusEvenAfterItLeftPending() {
        CreateResult first = fx.service.create(command("key-1"));
        fx.service.applyPaymentResult(first.reservation().reservationId(), "e-1", Outcome.APPROVED);

        CreateResult again = fx.service.create(command("key-1"));

        assertThat(again.created()).isFalse();
        assertThat(again.reservation().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(again.reservation().reservationId()).isEqualTo(first.reservation().reservationId());
    }

    @Test
    void differentKeyWhilePendingIsRejectedWithTheExistingReservationId() {
        CreateResult first = fx.service.create(command("key-1"));

        assertThatThrownBy(() -> fx.service.create(command("key-2")))
                .isInstanceOfSatisfying(ReservationException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.PENDING_EXISTS);
                    assertThat(e.reservationId()).isEqualTo(first.reservation().reservationId());
                });
        assertThat(fx.searchCalls.get()).isEqualTo(1);
        assertThat(fx.sink.count()).isEqualTo(1);
    }

    @Test
    void anotherSessionIsNotBlockedByPendingReservation() {
        fx.service.create(command("key-1"));

        CreateResult other = fx.service.create(new CreateCommand(OTHER_SESSION, "pass-1", 2, "key-1"));

        assertThat(other.created()).isTrue();
    }

    @Test
    void newReservationIsAllowedAfterPaymentFailure() {
        CreateResult first = fx.service.create(command("key-1"));
        fx.service.applyPaymentResult(first.reservation().reservationId(), "e-1", Outcome.FAILED);

        CreateResult retry = fx.service.create(new CreateCommand(SESSION, "pass-2", 3, "key-2"));

        assertThat(retry.created()).isTrue();
        assertThat(retry.reservation().reservationId()).isNotEqualTo(first.reservation().reservationId());
        assertThat(fx.store.findById(first.reservation().reservationId())).get()
                .extracting(Reservation::status).isEqualTo(ReservationStatus.PAYMENT_FAILED);
    }

    @Test
    void sameKeyRequestedConcurrentlyCreatesOneReservationWith201And200() throws Exception {
        CyclicBarrier bothInsideU1 = new CyclicBarrier(2);
        fx.search = (passId, hours) -> {
            await(bothInsideU1);
            return new PassInfo(passId, "강남 주차장", hours, 6000);
        };

        List<CreateResult> results = runConcurrently(2, () -> fx.service.create(command("key-1")));

        assertThat(results).extracting(CreateResult::created).containsExactlyInAnyOrder(true, false);
        assertThat(results.get(0).reservation().reservationId())
                .isEqualTo(results.get(1).reservation().reservationId());
        assertThat(fx.sink.count()).isEqualTo(1);
        assertThat(fx.scheduler.scheduled()).hasSize(1);
    }

    @Test
    void differentKeysRequestedConcurrentlyLeaveOnePendingReservation() throws Exception {
        CyclicBarrier bothInsideU1 = new CyclicBarrier(2);
        fx.search = (passId, hours) -> {
            await(bothInsideU1);
            return new PassInfo(passId, "강남 주차장", hours, 6000);
        };
        List<Callable<Object>> calls = new ArrayList<>();
        for (String key : new String[] { "key-a", "key-b" }) {
            calls.add(() -> {
                try {
                    return fx.service.create(command(key));
                } catch (ReservationException e) {
                    return e;
                }
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> f : pool.invokeAll(calls)) {
                outcomes.add(f.get(5, TimeUnit.SECONDS));
            }
            assertThat(outcomes.stream().filter(CreateResult.class::isInstance)).hasSize(1);
            assertThat(outcomes.stream().filter(ReservationException.class::isInstance)).hasSize(1);
            assertThat(((ReservationException) outcomes.stream().filter(ReservationException.class::isInstance)
                    .findFirst().orElseThrow()).errorCode()).isEqualTo(ErrorCode.PENDING_EXISTS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(fx.store.findPending(SESSION)).isPresent();
        assertThat(fx.sink.count()).isEqualTo(1);
    }

    @Test
    void u1IsCalledOutsideTheSessionLock() throws Exception {
        AtomicBoolean sessionLockWasFreeDuringCall = new AtomicBoolean();
        fx.search = (passId, hours) -> {
            ExecutorService other = Executors.newSingleThreadExecutor();
            try {
                sessionLockWasFreeDuringCall.set(other
                        .submit(() -> fx.store.withSessionLock(SESSION, () -> true)).get(1, TimeUnit.SECONDS));
            } catch (Exception e) {
                sessionLockWasFreeDuringCall.set(false);
            } finally {
                other.shutdownNow();
            }
            return new PassInfo(passId, "강남 주차장", hours, 6000);
        };

        fx.service.create(command("key-1"));

        assertThat(sessionLockWasFreeDuringCall).isTrue();
    }

    @Test
    void passNotFoundFromU1IsPassedThroughAndNothingIsCreated() {
        fx.search = (passId, hours) -> {
            throw new ReservationException(ErrorCode.PASS_NOT_FOUND);
        };

        assertCode(() -> fx.service.create(command("key-1")), ErrorCode.PASS_NOT_FOUND);
        assertThat(fx.store.findPending(SESSION)).isEmpty();
        assertThat(fx.sink.count()).isZero();
    }

    @Test
    void durationNotAvailableFromU1IsPassedThrough() {
        fx.search = (passId, hours) -> {
            throw new ReservationException(ErrorCode.DURATION_NOT_AVAILABLE);
        };

        assertCode(() -> fx.service.create(command("key-1")), ErrorCode.DURATION_NOT_AVAILABLE);
        assertThat(fx.scheduler.scheduled()).isEmpty();
    }

    @Test
    void u1FailureBecomes502AndTheSameKeyCanBeRetried() {
        fx.search = (passId, hours) -> {
            throw new ReservationException(ErrorCode.UPSTREAM_FAILED);
        };
        assertCode(() -> fx.service.create(command("key-1")), ErrorCode.UPSTREAM_FAILED);

        fx.search = (passId, hours) -> new PassInfo(passId, "강남 주차장", hours, 6000);
        assertThat(fx.service.create(command("key-1")).created()).isTrue();
    }

    private <T> List<T> runConcurrently(int threads, Callable<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<T>> calls = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                calls.add(call);
            }
            List<T> results = new ArrayList<>();
            for (Future<T> f : pool.invokeAll(calls)) {
                results.add(f.get(5, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

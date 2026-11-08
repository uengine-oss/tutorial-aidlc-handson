package kr.voiceparking.reservation.reservation;

import static kr.voiceparking.reservation.support.ServiceFixture.SESSION;
import static kr.voiceparking.reservation.support.ServiceFixture.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import kr.voiceparking.reservation.domain.Outcome;
import kr.voiceparking.reservation.domain.Reservation;
import kr.voiceparking.reservation.domain.ReservationStatus;
import kr.voiceparking.reservation.support.ManualScheduler;
import kr.voiceparking.reservation.support.ServiceFixture;

/** 자동 취소 W3와 타이머: 콜백이 취소, 종료된 예약에는 무동작, 해제, 승인과의 경합 (BR2.4, BR5.2, NFR2.1, NFR2.4). */
class DeadlineTimersTest {

    @Test
    void schedulesAtTheGivenInstantAndRunsTheTask() {
        ManualScheduler manual = new ManualScheduler();
        DeadlineTimers timers = new DeadlineTimers(manual.scheduler());
        AtomicInteger runs = new AtomicInteger();

        timers.schedule("r-1", T0.plusSeconds(180), runs::incrementAndGet);

        assertThat(manual.only().at()).isEqualTo(T0.plusSeconds(180));
        manual.only().task().run();
        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void cancelCancelsTheScheduledFutureOnce() {
        ManualScheduler manual = new ManualScheduler();
        DeadlineTimers timers = new DeadlineTimers(manual.scheduler());
        timers.schedule("r-1", T0.plusSeconds(180), () -> { });

        timers.cancel("r-1");
        timers.cancel("r-1");
        timers.cancel("unknown");

        verify(manual.only().future()).cancel(false);
    }

    @Test
    void schedulingAgainForTheSameReservationReplacesTheEarlierTimer() {
        ManualScheduler manual = new ManualScheduler();
        DeadlineTimers timers = new DeadlineTimers(manual.scheduler());
        timers.schedule("r-1", T0.plusSeconds(10), () -> { });
        timers.schedule("r-1", T0.plusSeconds(20), () -> { });

        verify(manual.scheduled().get(0).future()).cancel(false);
        verify(manual.scheduled().get(1).future(), never()).cancel(false);
    }

    @Test
    void timerCallbackCancelsAPendingReservationWithNoticeAndPush() {
        ServiceFixture fx = new ServiceFixture();
        String id = fx.service.create(new CreateCommand(SESSION, "pass-1", 2, "key-1")).reservation()
                .reservationId();
        fx.clock.advance(Duration.ofSeconds(180));

        fx.scheduler.only().task().run();

        Reservation r = fx.store.findById(id).orElseThrow();
        assertThat(r.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(r.cancelledAt()).isEqualTo(T0.plusSeconds(180));
        assertThat(r.message()).isEqualTo("승인 지연으로 예약이 취소되었습니다");
        assertThat(r.version()).isEqualTo(2);
        assertThat(fx.sink.count()).isEqualTo(2);
        assertThat(fx.sink.last().status()).isEqualTo("CANCELLED");
    }

    @Test
    void timerCallbackDoesNothingForATerminalReservation() {
        ServiceFixture fx = new ServiceFixture();
        String id = fx.service.create(new CreateCommand(SESSION, "pass-1", 2, "key-1")).reservation()
                .reservationId();
        fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);
        fx.clock.advance(Duration.ofSeconds(181));

        fx.scheduler.only().task().run();

        assertThat(fx.store.findById(id).orElseThrow().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(fx.store.findById(id).orElseThrow().version()).isEqualTo(2);
        assertThat(fx.sink.count()).isEqualTo(2);
    }

    @Test
    void timerCallbackForAnUnknownReservationIsIgnored() {
        ServiceFixture fx = new ServiceFixture();

        fx.service.cancelOnDeadline("missing");

        assertThat(fx.sink.count()).isZero();
    }

    @Test
    void timerIsReleasedWhenTheReservationLeavesPending() {
        ServiceFixture fx = new ServiceFixture();
        String id = fx.service.create(new CreateCommand(SESSION, "pass-1", 2, "key-1")).reservation()
                .reservationId();

        fx.service.applyPaymentResult(id, "e-1", Outcome.FAILED);

        verify(fx.scheduler.only().future()).cancel(false);
    }

    @Test
    void approvalRacingWithTheTimerHasExactlyOneWinner() throws Exception {
        for (int round = 0; round < 50; round++) {
            ServiceFixture fx = new ServiceFixture();
            String id = fx.service.create(new CreateCommand(SESSION, "pass-1", 2, "key-" + round)).reservation()
                    .reservationId();
            Runnable timer = fx.scheduler.only().task();
            CyclicBarrier start = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Callable<Object>> calls = new ArrayList<>();
                calls.add(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);
                });
                calls.add(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    timer.run();
                    return null;
                });
                pool.invokeAll(calls).forEach(f -> {
                    try {
                        f.get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                });
            } finally {
                pool.shutdownNow();
            }

            Reservation r = fx.store.findById(id).orElseThrow();
            assertThat(r.status()).isIn(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED);
            assertThat(r.version()).isEqualTo(2);
            assertThat(r.confirmedAt() != null ^ r.cancelledAt() != null).isTrue();
            // 생성 푸시 하나와 전이 푸시 하나뿐이다. 두 전이가 모두 일어나지 않았다.
            assertThat(fx.sink.count()).isEqualTo(2);
            assertThat(fx.sink.last().status()).isEqualTo(r.status().name());
        }
    }
}

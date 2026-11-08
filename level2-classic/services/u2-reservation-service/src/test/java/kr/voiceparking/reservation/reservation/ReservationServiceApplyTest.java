package kr.voiceparking.reservation.reservation;

import static kr.voiceparking.reservation.support.ServiceFixture.OTHER_SESSION;
import static kr.voiceparking.reservation.support.ServiceFixture.SESSION;
import static kr.voiceparking.reservation.support.ServiceFixture.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kr.voiceparking.reservation.domain.AppliedEvent;
import kr.voiceparking.reservation.domain.ApplyResult;
import kr.voiceparking.reservation.domain.Outcome;
import kr.voiceparking.reservation.domain.Reservation;
import kr.voiceparking.reservation.domain.ReservationStatus;
import kr.voiceparking.reservation.support.ServiceFixture;

/** 결제 결과 반영 W2와 조회: 원자 반영, 멱등, 180초 경계, 세션 격리 (BR2.1~BR2.6, BR3.1~BR3.3, BR5.2). */
class ReservationServiceApplyTest {

    private final ServiceFixture fx = new ServiceFixture();
    private String id;

    @BeforeEach
    void createPendingReservation() {
        id = fx.service.create(new CreateCommand(SESSION, "pass-1", 2, "key-1")).reservation().reservationId();
    }

    private Reservation stored() {
        return fx.store.findById(id).orElseThrow();
    }

    @Test
    void approvalConfirmsAndPushesOnce() {
        AppliedEvent event = fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);

        assertThat(event.result()).isEqualTo(ApplyResult.APPLIED);
        assertThat(event.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(stored().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(stored().version()).isEqualTo(2);
        assertThat(stored().confirmedAt()).isEqualTo(T0);
        assertThat(fx.sink.count()).isEqualTo(2);
        assertThat(fx.sink.last().status()).isEqualTo("CONFIRMED");
        assertThat(fx.sink.last().message()).isEqualTo("예약이 완료되었습니다");
    }

    @Test
    void failureMovesToPaymentFailedAndStaysSoOverTime() {
        AppliedEvent event = fx.service.applyPaymentResult(id, "e-1", Outcome.FAILED);

        assertThat(event.result()).isEqualTo(ApplyResult.APPLIED);
        assertThat(event.reservationStatus()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
        assertThat(stored().message()).startsWith("결제가 거절되었습니다");

        fx.clock.advance(Duration.ofSeconds(500));
        AppliedEvent late = fx.service.applyPaymentResult(id, "e-2", Outcome.APPROVED);
        assertThat(late.result()).isEqualTo(ApplyResult.REJECTED);
        assertThat(stored().status()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
        assertThat(stored().version()).isEqualTo(2);
    }

    @Test
    void sameEventIdThreeTimesConfirmsOnceAndReturnsTheFirstDecision() {
        AppliedEvent first = fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);
        AppliedEvent second = fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);
        AppliedEvent third = fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);

        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
        assertThat(first.result()).isEqualTo(ApplyResult.APPLIED);
        assertThat(stored().version()).isEqualTo(2);
        assertThat(fx.sink.count()).isEqualTo(2);
    }

    @Test
    void sameEventIdFromManyThreadsChangesStateExactlyOnce() throws Exception {
        List<Callable<AppliedEvent>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<AppliedEvent> results = new ArrayList<>();
            for (Future<AppliedEvent> f : pool.invokeAll(calls)) {
                results.add(f.get(5, TimeUnit.SECONDS));
            }
            assertThat(results).allMatch(r -> r.equals(results.get(0)));
            assertThat(results.get(0).result()).isEqualTo(ApplyResult.APPLIED);
        } finally {
            pool.shutdownNow();
        }
        assertThat(stored().version()).isEqualTo(2);
        assertThat(fx.sink.count()).isEqualTo(2);
    }

    @Test
    void secondApprovalWithAnotherEventIdIsRejectedWithoutPush() {
        fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);

        AppliedEvent second = fx.service.applyPaymentResult(id, "e-2", Outcome.APPROVED);

        assertThat(second.result()).isEqualTo(ApplyResult.REJECTED);
        assertThat(second.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(stored().version()).isEqualTo(2);
        assertThat(fx.sink.count()).isEqualTo(2);
        // 거부도 판정 기록으로 남아 같은 eventId 재요청에 같은 판정을 돌려준다.
        assertThat(fx.service.applyPaymentResult(id, "e-2", Outcome.APPROVED)).isEqualTo(second);
    }

    @Test
    void approvalAt179SecondsConfirmsAndReleasesTheTimer() {
        fx.clock.advance(Duration.ofSeconds(179));

        AppliedEvent event = fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);

        assertThat(event.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(fx.scheduler.only().future()).cancel(false);
    }

    @Test
    void approvalAtTheDeadlineCancelsFirstAndThenRejects() {
        fx.clock.advance(Duration.ofSeconds(180));

        AppliedEvent event = fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);

        assertThat(event.result()).isEqualTo(ApplyResult.REJECTED);
        assertThat(event.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(stored().status()).isEqualTo(ReservationStatus.CANCELLED);
        // 마감 후 발견한 취소의 cancelledAt은 마감 시각이다 (D4).
        assertThat(stored().cancelledAt()).isEqualTo(T0.plusSeconds(180));
        assertThat(stored().message()).isEqualTo("승인 지연으로 예약이 취소되었습니다");
        assertThat(fx.sink.count()).isEqualTo(2);
        assertThat(fx.sink.last().status()).isEqualTo("CANCELLED");
        verify(fx.scheduler.only().future()).cancel(false);
    }

    @Test
    void approvalAt181SecondsCancelsAtTheDeadlineAndLaterApprovalDoesNotRevive() {
        fx.clock.advance(Duration.ofSeconds(181));

        AppliedEvent event = fx.service.applyPaymentResult(id, "e-1", Outcome.APPROVED);
        assertThat(event.result()).isEqualTo(ApplyResult.REJECTED);
        assertThat(event.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(stored().cancelledAt()).isEqualTo(T0.plusSeconds(180));

        fx.clock.advance(Duration.ofSeconds(60));
        AppliedEvent again = fx.service.applyPaymentResult(id, "e-2", Outcome.APPROVED);
        assertThat(again.result()).isEqualTo(ApplyResult.REJECTED);
        assertThat(stored().status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(stored().version()).isEqualTo(2);
    }

    @Test
    void failedResultAfterTheDeadlineAlsoCancels() {
        fx.clock.advance(Duration.ofSeconds(200));

        AppliedEvent event = fx.service.applyPaymentResult(id, "e-1", Outcome.FAILED);

        assertThat(event.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(event.result()).isEqualTo(ApplyResult.REJECTED);
    }

    @Test
    void unknownReservationIsNotFound() {
        assertThatThrownBy(() -> fx.service.applyPaymentResult("missing", "e-1", Outcome.APPROVED))
                .isInstanceOfSatisfying(ReservationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void invalidResultRequestIsRejectedBeforeAnyChange() {
        for (String badEventId : new String[] { null, "", "  ", "e".repeat(101) }) {
            assertThatThrownBy(() -> fx.service.applyPaymentResult(id, badEventId, Outcome.APPROVED))
                    .isInstanceOfSatisfying(ReservationException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        }
        assertThatThrownBy(() -> fx.service.applyPaymentResult(id, "e-1", null))
                .isInstanceOfSatisfying(ReservationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThat(stored().status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(fx.sink.count()).isEqualTo(1);
    }

    @Test
    void getReturnsOwnReservationOnly() {
        assertThat(fx.service.get(SESSION, id).reservationId()).isEqualTo(id);

        assertThatThrownBy(() -> fx.service.get(OTHER_SESSION, id)).isInstanceOfSatisfying(ReservationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> fx.service.get(SESSION, "missing")).isInstanceOfSatisfying(
                ReservationException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void currentReturnsTheLatestReservationOfTheSession() {
        fx.service.applyPaymentResult(id, "e-1", Outcome.FAILED);
        fx.clock.advance(Duration.ofSeconds(10));
        String second = fx.service.create(new CreateCommand(SESSION, "pass-2", 1, "key-2")).reservation()
                .reservationId();

        assertThat(fx.service.current(SESSION).reservationId()).isEqualTo(second);
    }

    @Test
    void currentWithoutReservationIsNoReservation() {
        assertThatThrownBy(() -> fx.service.current(OTHER_SESSION)).isInstanceOfSatisfying(
                ReservationException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NO_RESERVATION));
    }
}

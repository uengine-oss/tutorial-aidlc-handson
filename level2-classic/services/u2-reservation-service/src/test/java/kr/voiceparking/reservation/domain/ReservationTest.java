package kr.voiceparking.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import kr.voiceparking.reservation.notification.Snapshot;

/** 도메인: 초기 상태, 허용된 세 전이, 종료 상태 불변, 상태별 message와 시각 필드, Snapshot 변환 (BR1.5, BR2.5). */
class ReservationTest {

    private static final Instant T0 = Instant.parse("2026-11-07T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(30);

    private static Reservation pending() {
        return Reservation.create("r-1", "s-1", "key-1", "pass-1", "강남 주차장", 2, 6000, T0);
    }

    @Test
    void newReservationIsPendingWithVersionOne() {
        Reservation r = pending();

        assertThat(r.status()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(r.version()).isEqualTo(1);
        assertThat(r.paymentRequestedAt()).isEqualTo(T0);
        assertThat(r.message()).isEqualTo("결제 승인을 기다리는 중입니다");
        assertThat(r.confirmedAt()).isNull();
        assertThat(r.failedAt()).isNull();
        assertThat(r.cancelledAt()).isNull();
        assertThat(r.isPending()).isTrue();
    }

    @Test
    void deadlineIsPaymentRequestedAtPlusTimeout() {
        assertThat(pending().deadline(Duration.ofSeconds(180))).isEqualTo(T0.plusSeconds(180));
    }

    @Test
    void confirmMovesToConfirmedAndBumpsVersion() {
        Reservation r = pending().confirm(T1);

        assertThat(r.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(r.version()).isEqualTo(2);
        assertThat(r.confirmedAt()).isEqualTo(T1);
        assertThat(r.failedAt()).isNull();
        assertThat(r.cancelledAt()).isNull();
        assertThat(r.message()).isEqualTo("예약이 완료되었습니다");
    }

    @Test
    void paymentFailedMovesToPaymentFailedWithOnlyFailedAt() {
        Reservation r = pending().paymentFailed(T1);

        assertThat(r.status()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
        assertThat(r.version()).isEqualTo(2);
        assertThat(r.failedAt()).isEqualTo(T1);
        assertThat(r.confirmedAt()).isNull();
        assertThat(r.cancelledAt()).isNull();
        assertThat(r.message()).isEqualTo("결제가 거절되었습니다. 다른 주차권을 선택해 다시 결제해 주세요");
    }

    @Test
    void cancelMovesToCancelledWithOnlyCancelledAt() {
        Reservation r = pending().cancel(T1);

        assertThat(r.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(r.version()).isEqualTo(2);
        assertThat(r.cancelledAt()).isEqualTo(T1);
        assertThat(r.confirmedAt()).isNull();
        assertThat(r.failedAt()).isNull();
        assertThat(r.message()).isEqualTo("승인 지연으로 예약이 취소되었습니다");
    }

    @Test
    void terminalStatesNeverChange() {
        Reservation confirmed = pending().confirm(T1);
        Reservation failed = pending().paymentFailed(T1);
        Reservation cancelled = pending().cancel(T1);

        for (Reservation terminal : new Reservation[] { confirmed, failed, cancelled }) {
            assertThat(terminal.isPending()).isFalse();
            assertThat(terminal.confirm(T1.plusSeconds(1))).isEqualTo(terminal);
            assertThat(terminal.paymentFailed(T1.plusSeconds(1))).isEqualTo(terminal);
            assertThat(terminal.cancel(T1.plusSeconds(1))).isEqualTo(terminal);
            assertThat(terminal.version()).isEqualTo(2);
        }
    }

    @Test
    void snapshotCarriesPublicFieldsAndOmitsInternalOnes() {
        Snapshot pendingSnapshot = pending().toSnapshot();
        assertThat(pendingSnapshot.reservationId()).isEqualTo("r-1");
        assertThat(pendingSnapshot.version()).isEqualTo(1);
        assertThat(pendingSnapshot.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(pendingSnapshot.lotName()).isEqualTo("강남 주차장");
        assertThat(pendingSnapshot.durationHours()).isEqualTo(2);
        assertThat(pendingSnapshot.amount()).isEqualTo(6000);
        assertThat(pendingSnapshot.paymentRequestedAt()).isEqualTo(T0);
        assertThat(pendingSnapshot.confirmedAt()).isNull();
        assertThat(pendingSnapshot.cancelledAt()).isNull();

        Snapshot confirmedSnapshot = pending().confirm(T1).toSnapshot();
        assertThat(confirmedSnapshot.confirmedAt()).isEqualTo(T1);
        assertThat(confirmedSnapshot.cancelledAt()).isNull();

        Snapshot cancelledSnapshot = pending().cancel(T1).toSnapshot();
        assertThat(cancelledSnapshot.cancelledAt()).isEqualTo(T1);
        assertThat(cancelledSnapshot.confirmedAt()).isNull();
        assertThat(cancelledSnapshot.message()).isEqualTo("승인 지연으로 예약이 취소되었습니다");
    }
}

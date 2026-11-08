package com.voiceparking.reservation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private static final Instant T0 = Instant.parse("2026-11-05T00:00:00Z");

    private Reservation pending() {
        return new Reservation("r1", "T001", "강남역 공영", 6000, T0, T0.plusSeconds(180));
    }

    @Test
    void 대기에서_확정으로_전이한다() {
        Reservation r = pending();
        assertTrue(r.confirm(T0.plusSeconds(10)));
        assertEquals(ReservationStatus.CONFIRMED, r.getStatus());
        assertEquals(T0.plusSeconds(10), r.toView().confirmedAt());
    }

    @Test
    void 이미_확정이면_다시_확정해도_변화_없음() {
        Reservation r = pending();
        r.confirm(T0.plusSeconds(10));
        assertFalse(r.confirm(T0.plusSeconds(20)));
        assertEquals(T0.plusSeconds(10), r.toView().confirmedAt());
    }

    @Test
    void 취소된_예약은_확정할_수_없다() {
        Reservation r = pending();
        assertTrue(r.cancel());
        assertThrows(InvalidTransitionException.class, () -> r.confirm(T0.plusSeconds(1)));
        assertEquals(ReservationStatus.CANCELLED, r.getStatus());
    }

    @Test
    void 확정된_예약은_취소되지_않는다() {
        Reservation r = pending();
        r.confirm(T0);
        assertFalse(r.cancel());
        assertEquals(ReservationStatus.CONFIRMED, r.getStatus());
    }

    @Test
    void 정확히_마감_시각이면_만료로_본다() {
        Reservation r = pending();
        assertFalse(r.isExpired(T0.plusSeconds(179)));
        assertTrue(r.isExpired(T0.plusSeconds(180)));
    }
}

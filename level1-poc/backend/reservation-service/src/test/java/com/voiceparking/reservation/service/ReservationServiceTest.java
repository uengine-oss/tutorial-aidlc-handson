package com.voiceparking.reservation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.voiceparking.reservation.domain.InvalidTransitionException;
import com.voiceparking.reservation.domain.ReservationStatus;
import com.voiceparking.reservation.domain.ReservationView;
import com.voiceparking.reservation.event.SseHub;
import com.voiceparking.reservation.repository.ReservationRepository;
import com.voiceparking.reservation.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {

    private MutableClock clock;
    private SseHub hub;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-05T00:00:00Z"));
        hub = new SseHub();
        service = new ReservationService(new ReservationRepository(), clock, hub, 180);
    }

    @Test
    void 생성하면_대기_상태이고_마감은_180초_뒤() {
        ReservationView v = service.create("T001", "강남역 공영", 6000);
        assertEquals(ReservationStatus.PENDING, v.status());
        assertEquals(v.createdAt().plusSeconds(180), v.deadlineAt());
    }

    @Test
    void 대기_예약을_확정한다() {
        String id = service.create("T001", "강남역 공영", 6000).id();
        assertEquals(ReservationStatus.CONFIRMED, service.confirm(id).status());
    }

    @Test
    void 마감이_지난_예약은_스케줄러가_취소한다() {
        String id = service.create("T001", "강남역 공영", 6000).id();
        clock.advance(Duration.ofSeconds(179));
        assertEquals(0, service.expireOverdue());
        clock.advance(Duration.ofSeconds(2));
        assertEquals(1, service.expireOverdue());
        assertEquals(ReservationStatus.CANCELLED, service.get(id).status());
    }

    @Test
    void 정확히_180초에도_취소된다() {
        String id = service.create("T001", "강남역 공영", 6000).id();
        clock.advance(Duration.ofSeconds(180));
        assertEquals(1, service.expireOverdue());
        assertEquals(ReservationStatus.CANCELLED, service.get(id).status());
    }

    @Test
    void 취소된_예약에_늦은_승인이_오면_거절한다() {
        String id = service.create("T001", "강남역 공영", 6000).id();
        clock.advance(Duration.ofSeconds(200));
        service.expireOverdue();
        assertThrows(InvalidTransitionException.class, () -> service.confirm(id));
        assertEquals(ReservationStatus.CANCELLED, service.get(id).status());
    }

    @Test
    void 스케줄러가_아직_돌기_전이라도_마감이_지난_확정_요청은_거절한다() {
        String id = service.create("T001", "강남역 공영", 6000).id();
        clock.advance(Duration.ofSeconds(181));
        assertThrows(InvalidTransitionException.class, () -> service.confirm(id));
        assertEquals(ReservationStatus.CANCELLED, service.get(id).status());
    }

    @Test
    void 모르는_예약은_NotFound() {
        assertThrows(ReservationNotFoundException.class, () -> service.confirm("nope"));
    }

    @Test
    void 확정_이벤트가_구독자에게_1초_이내에_도착한다() {
        String id = service.create("T001", "강남역 공영", 6000).id();
        List<ReservationView> received = new ArrayList<>();
        hub.subscribe(id, received::add);

        long start = System.nanoTime();
        service.confirm(id);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(1, received.size());
        assertEquals(ReservationStatus.CONFIRMED, received.get(0).status());
        assertTrue(elapsedMs < 1000, "NFR1 위반: " + elapsedMs + "ms");
    }

    @Test
    void 취소_이벤트도_구독자에게_전달된다() {
        String id = service.create("T001", "강남역 공영", 6000).id();
        List<ReservationView> received = new ArrayList<>();
        hub.subscribe(id, received::add);
        clock.advance(Duration.ofSeconds(180));
        service.expireOverdue();
        assertEquals(ReservationStatus.CANCELLED, received.get(0).status());
    }
}

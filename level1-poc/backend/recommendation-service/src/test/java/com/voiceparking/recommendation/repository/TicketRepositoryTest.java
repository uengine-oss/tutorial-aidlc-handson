package com.voiceparking.recommendation.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.voiceparking.recommendation.domain.ParkingTicket;
import java.util.List;
import org.junit.jupiter.api.Test;

class TicketRepositoryTest {

    private final TicketRepository repo = new TicketRepository(List.of(
            new ParkingTicket("A", "가까운 곳", "강남역", 5000, 999, 2),
            new ParkingTicket("B", "경계", "강남역", 5000, 1000, 2),
            new ParkingTicket("C", "먼 곳", "강남역", 5000, 1001, 2),
            new ParkingTicket("D", "다른 역", "홍대입구역", 5000, 100, 2)));

    @Test
    void 반경_1000m_이내만_경계_포함으로_거른다() {
        List<ParkingTicket> near = repo.findNear("강남역", 1000);
        assertEquals(List.of("A", "B"), near.stream().map(ParkingTicket::id).toList());
    }

    @Test
    void 모르는_목적지는_빈_목록() {
        assertTrue(repo.findNear("부산역", 1000).isEmpty());
    }

    @Test
    void 알려진_목적지_목록을_돌려준다() {
        assertTrue(repo.knownDestinations().containsAll(List.of("강남역", "홍대입구역")));
    }
}

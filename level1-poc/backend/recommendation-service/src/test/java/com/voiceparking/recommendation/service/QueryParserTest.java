package com.voiceparking.recommendation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.voiceparking.recommendation.domain.ParkingTicket;
import com.voiceparking.recommendation.domain.ParsedQuery;
import com.voiceparking.recommendation.repository.TicketRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QueryParserTest {

    private final QueryParser parser = new QueryParser(new TicketRepository(List.of(
            new ParkingTicket("A", "a", "강남역", 5000, 100, 2))));

    @Test
    void 강남역_근처_2시간을_해석한다() {
        Optional<ParsedQuery> q = parser.parse("강남역 근처 2시간 주차권 찾아줘");
        assertEquals(new ParsedQuery("강남역", 2), q.orElseThrow());
    }

    @Test
    void 한글_숫자_두_시간을_해석한다() {
        assertEquals(2, parser.parse("강남역 근처 두 시간 주차권").orElseThrow().hours());
    }

    @Test
    void 데이터에_없는_역도_일반_규칙으로_목적지를_찾는다() {
        assertEquals("서울역", parser.parse("서울역 3시간 주차").orElseThrow().destination());
    }

    @Test
    void 목적지와_시간이_없으면_빈_값() {
        assertTrue(parser.parse("주차권 찾아줘").isEmpty());
        assertTrue(parser.parse("강남역 근처 주차권").isEmpty());
        assertTrue(parser.parse("2시간만").isEmpty());
    }

    @Test
    void 빈_입력과_범위_밖_시간은_거절한다() {
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("강남역 0시간").isEmpty());
        assertTrue(parser.parse("강남역 99시간").isEmpty());
    }
}

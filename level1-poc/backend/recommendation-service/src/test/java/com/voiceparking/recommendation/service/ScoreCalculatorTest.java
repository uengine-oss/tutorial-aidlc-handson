package com.voiceparking.recommendation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.voiceparking.recommendation.domain.ParkingTicket;
import com.voiceparking.recommendation.domain.ScoredTicket;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

    private final ScoreCalculator calculator = new ScoreCalculator();

    private static ParkingTicket t(String id, int price, int dist, int hours) {
        return new ParkingTicket(id, id, "강남역", price, dist, hours);
    }

    @Test
    void 가격_거리_적합도를_40_40_20으로_합산한다() {
        List<ScoredTicket> ranked = calculator.score(List.of(
                t("T001", 6000, 250, 2), t("T002", 5000, 600, 2), t("T003", 8000, 100, 2)), 2);

        assertEquals(List.of("T003", "T001", "T002"), ranked.stream().map(s -> s.ticket().id()).toList());
        assertEquals(85.0, ranked.get(0).totalScore(), 0.01);
        assertEquals(69.33, ranked.get(1).totalScore(), 0.01);
        assertEquals(66.67, ranked.get(2).totalScore(), 0.01);
    }

    @Test
    void 같은_입력은_같은_결과() {
        List<ParkingTicket> in = List.of(t("A", 6000, 250, 2), t("B", 5000, 600, 3), t("C", 8000, 100, 1));
        assertEquals(calculator.score(in, 2), calculator.score(in, 2));
    }

    @Test
    void 동점이면_가격이_낮은_쪽이_1위() {
        // A: 가격 100점/거리 50점, B: 가격 50점/거리 100점 -> 둘 다 80점
        List<ScoredTicket> ranked = calculator.score(List.of(
                t("B", 8000, 100, 2), t("A", 4000, 200, 2)), 2);
        assertEquals(ranked.get(0).totalScore(), ranked.get(1).totalScore(), 0.0);
        assertEquals("A", ranked.get(0).ticket().id());
    }

    @Test
    void 이용_시간이_어긋나면_1시간당_50점_감점하고_0점_밑으로_내려가지_않는다() {
        List<ScoredTicket> ranked = calculator.score(List.of(
                t("X", 5000, 100, 3), t("Y", 5000, 100, 6)), 2);
        ScoredTicket x = ranked.stream().filter(s -> s.ticket().id().equals("X")).findFirst().orElseThrow();
        ScoredTicket y = ranked.stream().filter(s -> s.ticket().id().equals("Y")).findFirst().orElseThrow();
        assertEquals(50.0, x.fitScore(), 0.0);
        assertEquals(0.0, y.fitScore(), 0.0);
        assertTrue(x.totalScore() > y.totalScore());
    }

    @Test
    void 후보가_없으면_빈_목록() {
        assertTrue(calculator.score(List.of(), 2).isEmpty());
    }
}

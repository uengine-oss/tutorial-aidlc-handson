package com.voiceparking.recommendation.service;

import com.voiceparking.recommendation.domain.ParkingTicket;
import com.voiceparking.recommendation.domain.ScoredTicket;
import java.util.Comparator;
import java.util.List;

/**
 * 종합 점수 = 가격 x 0.4 + 거리 x 0.4 + 이용 시간 적합도 x 0.2 (FR2.1).
 * 가격·거리는 후보 중 최솟값 대비 비율(0~100, 낮을수록 고득점), 적합도는 요청 시간과 같으면 100,
 * 1시간 어긋날 때마다 50점 감점(최소 0). 동점이면 가격이 낮은 쪽, 그래도 같으면 ID 순.
 */
public class ScoreCalculator {

    static final double PRICE_WEIGHT = 0.4;
    static final double DISTANCE_WEIGHT = 0.4;
    static final double FIT_WEIGHT = 0.2;
    static final double FIT_PENALTY_PER_HOUR = 50.0;

    /** 점수 내림차순으로 정렬된 목록을 돌려준다. best/reason은 비워 둔다. */
    public List<ScoredTicket> score(List<ParkingTicket> candidates, int requestedHours) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        int minPrice = candidates.stream().mapToInt(ParkingTicket::price).min().orElseThrow();
        int minDistance = candidates.stream().mapToInt(ParkingTicket::distanceMeters).min().orElseThrow();

        return candidates.stream()
                .map(t -> {
                    double priceScore = ratio(minPrice, t.price());
                    double distanceScore = ratio(minDistance, t.distanceMeters());
                    double fitScore = Math.max(0,
                            100 - FIT_PENALTY_PER_HOUR * Math.abs(t.durationHours() - requestedHours));
                    double total = priceScore * PRICE_WEIGHT
                            + distanceScore * DISTANCE_WEIGHT
                            + fitScore * FIT_WEIGHT;
                    return new ScoredTicket(t, round(priceScore), round(distanceScore),
                            round(fitScore), round(total), false, null);
                })
                .sorted(Comparator.comparingDouble(ScoredTicket::totalScore).reversed()
                        .thenComparingInt(s -> s.ticket().price())
                        .thenComparing(s -> s.ticket().id()))
                .toList();
    }

    private static double ratio(int min, int value) {
        if (value <= 0) {
            return 100.0;
        }
        return (double) min / value * 100.0;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

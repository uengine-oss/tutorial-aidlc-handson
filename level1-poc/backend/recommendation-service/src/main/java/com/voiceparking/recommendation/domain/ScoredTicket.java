package com.voiceparking.recommendation.domain;

/**
 * 점수가 매겨진 후보. best가 true이면 최적 추천이며 reason에 추천 이유가 담긴다.
 */
public record ScoredTicket(
        ParkingTicket ticket,
        double priceScore,
        double distanceScore,
        double fitScore,
        double totalScore,
        boolean best,
        String reason) {

    public ScoredTicket withBest(boolean newBest, String newReason) {
        return new ScoredTicket(ticket, priceScore, distanceScore, fitScore, totalScore, newBest, newReason);
    }
}

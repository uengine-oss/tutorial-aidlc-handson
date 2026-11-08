package com.voiceparking.recommendation.service;

import com.voiceparking.recommendation.domain.ScoredTicket;
import java.util.List;

/** 최적 후보가 왜 1위인지 한국어로 설명한다 (FR2.3). */
public class ExplanationGenerator {

    public String explain(ScoredTicket best, List<ScoredTicket> all, int requestedHours) {
        int minPrice = all.stream().mapToInt(s -> s.ticket().price()).min().orElse(best.ticket().price());
        int minDistance = all.stream().mapToInt(s -> s.ticket().distanceMeters()).min()
                .orElse(best.ticket().distanceMeters());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("종합 점수 %.1f점으로 1위입니다. ", best.totalScore()));
        sb.append(best.ticket().price() == minPrice
                ? "가격이 후보 중 가장 저렴하고(" + best.ticket().price() + "원), "
                : "가격은 " + best.ticket().price() + "원이며, ");
        sb.append(best.ticket().distanceMeters() == minDistance
                ? "목적지와 가장 가깝습니다(" + best.ticket().distanceMeters() + "m). "
                : "목적지까지 " + best.ticket().distanceMeters() + "m입니다. ");
        if (best.ticket().durationHours() == requestedHours) {
            sb.append("요청하신 ").append(requestedHours).append("시간과 이용 시간이 정확히 맞습니다.");
        } else {
            sb.append("이용 시간은 ").append(best.ticket().durationHours())
                    .append("시간으로 요청(").append(requestedHours).append("시간)과 차이가 있지만 다른 항목이 더 좋습니다.");
        }
        return sb.toString();
    }
}

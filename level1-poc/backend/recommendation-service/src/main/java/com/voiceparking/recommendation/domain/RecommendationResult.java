package com.voiceparking.recommendation.domain;

import java.util.List;

/**
 * 추천 응답. status는 OK(추천 있음), NEED_RETRY(입력 해석 실패), NO_RESULT(후보 없음) 중 하나.
 */
public record RecommendationResult(
        String status,
        String message,
        String destination,
        Integer hours,
        List<ScoredTicket> candidates,
        String notice) {

    public static final String OK = "OK";
    public static final String NEED_RETRY = "NEED_RETRY";
    public static final String NO_RESULT = "NO_RESULT";

    public static RecommendationResult needRetry(String message) {
        return new RecommendationResult(NEED_RETRY, message, null, null, List.of(), null);
    }
}

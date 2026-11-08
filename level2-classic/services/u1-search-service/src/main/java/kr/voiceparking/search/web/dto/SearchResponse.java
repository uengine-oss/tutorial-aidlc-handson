package kr.voiceparking.search.web.dto;

import java.util.List;

import kr.voiceparking.search.domain.Factor;

/** C1 응답. 필드 이름은 contract-summary.md의 C1을 따른다. */
public record SearchResponse(QueryDto query, List<RecommendationDto> recommendations) {

    public record QueryDto(String place, int durationHours) {
    }

    public record RecommendationDto(
            String passId,
            String lotName,
            int price,
            int distanceMeters,
            int score,
            Rank rank,
            ReasonDto reason) {
    }

    public record ReasonDto(Factor topFactor, String text) {
    }

    public enum Rank {
        BEST, CANDIDATE
    }
}

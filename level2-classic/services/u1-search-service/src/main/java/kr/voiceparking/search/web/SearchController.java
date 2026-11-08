package kr.voiceparking.search.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import kr.voiceparking.search.domain.ParsedQuery;
import kr.voiceparking.search.domain.ScoredPass;
import kr.voiceparking.search.service.ErrorCode;
import kr.voiceparking.search.service.QueryParser;
import kr.voiceparking.search.service.ReasonFormatter;
import kr.voiceparking.search.service.RecommendationService;
import kr.voiceparking.search.service.SearchException;
import kr.voiceparking.search.web.dto.SearchRequest;
import kr.voiceparking.search.web.dto.SearchResponse;
import kr.voiceparking.search.web.dto.SearchResponse.QueryDto;
import kr.voiceparking.search.web.dto.SearchResponse.Rank;
import kr.voiceparking.search.web.dto.SearchResponse.ReasonDto;
import kr.voiceparking.search.web.dto.SearchResponse.RecommendationDto;

/** C1 추천 요청: POST /api/search/recommendations. */
@RestController
public class SearchController {

    static final int MAX_TEXT_LENGTH = 200;
    static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final QueryParser queryParser;
    private final RecommendationService recommendationService;
    private final ReasonFormatter reasonFormatter;

    public SearchController(QueryParser queryParser, RecommendationService recommendationService,
            ReasonFormatter reasonFormatter) {
        this.queryParser = queryParser;
        this.recommendationService = recommendationService;
        this.reasonFormatter = reasonFormatter;
    }

    @PostMapping("/api/search/recommendations")
    public SearchResponse recommend(
            @RequestHeader(name = "X-Session-Id", required = false) String sessionId,
            @RequestBody SearchRequest request) {
        // 경계 입력 검증은 서비스 로직보다 먼저 한다 (BR1.5, BR1.6).
        if (sessionId == null || !UUID_FORMAT.matcher(sessionId).matches()) {
            throw new SearchException(ErrorCode.MISSING_SESSION);
        }
        String text = request == null ? null : request.text();
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_LENGTH) {
            throw new SearchException(ErrorCode.INVALID_TEXT);
        }

        ParsedQuery query = queryParser.parse(text);
        List<ScoredPass> ranked = recommendationService.recommend(query);

        List<RecommendationDto> recommendations = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            ScoredPass s = ranked.get(i);
            recommendations.add(new RecommendationDto(
                    s.pass().passId(), s.lot().name(), s.totalPrice(), s.lot().distanceMeters(),
                    s.totalScore(), i == 0 ? Rank.BEST : Rank.CANDIDATE,
                    new ReasonDto(s.topFactor(), reasonFormatter.format(s))));
        }
        return new SearchResponse(new QueryDto(query.placeName(), query.durationHours()), recommendations);
    }
}

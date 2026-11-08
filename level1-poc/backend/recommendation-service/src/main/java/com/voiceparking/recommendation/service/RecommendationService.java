package com.voiceparking.recommendation.service;

import com.voiceparking.recommendation.domain.ParkingTicket;
import com.voiceparking.recommendation.domain.ParsedQuery;
import com.voiceparking.recommendation.domain.RecommendationResult;
import com.voiceparking.recommendation.domain.ScoredTicket;
import com.voiceparking.recommendation.repository.TicketRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 텍스트 해석 -> 후보 탐색 -> 점수화 -> 상위 3개와 설명 (FR1~FR2). */
public class RecommendationService {

    static final int MAX_CANDIDATES = 3;

    private final TicketRepository repository;
    private final QueryParser parser;
    private final ScoreCalculator calculator;
    private final ExplanationGenerator explainer;
    private final int radiusMeters;

    public RecommendationService(TicketRepository repository, QueryParser parser,
                                 ScoreCalculator calculator, ExplanationGenerator explainer,
                                 int radiusMeters) {
        this.repository = repository;
        this.parser = parser;
        this.calculator = calculator;
        this.explainer = explainer;
        this.radiusMeters = radiusMeters;
    }

    public RecommendationResult recommend(String text) {
        Optional<ParsedQuery> parsed = parser.parse(text);
        if (parsed.isEmpty()) {
            return RecommendationResult.needRetry(
                    "목적지와 이용 시간을 알아듣지 못했어요. 예: \"강남역 근처 2시간 주차권 찾아줘\"");
        }
        ParsedQuery q = parsed.get();
        List<ParkingTicket> near = repository.findNear(q.destination(), radiusMeters);
        if (near.isEmpty()) {
            return new RecommendationResult(RecommendationResult.NO_RESULT,
                    q.destination() + " 반경 " + radiusMeters + "m 안에 주차권이 없습니다.",
                    q.destination(), q.hours(), List.of(), null);
        }

        List<ScoredTicket> ranked = calculator.score(near, q.hours());
        List<ScoredTicket> top = new ArrayList<>(ranked.subList(0, Math.min(MAX_CANDIDATES, ranked.size())));
        ScoredTicket best = top.get(0);
        top.set(0, best.withBest(true, explainer.explain(best, ranked, q.hours())));

        String notice = near.size() < MAX_CANDIDATES
                ? "비교할 후보가 부족합니다. 찾은 " + near.size() + "개만 보여 드려요."
                : null;
        return new RecommendationResult(RecommendationResult.OK,
                q.destination() + " " + q.hours() + "시간 주차권 추천 결과입니다.",
                q.destination(), q.hours(), top, notice);
    }
}

package kr.voiceparking.search.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

import kr.voiceparking.search.data.ParkingRepository;
import kr.voiceparking.search.domain.Factor;
import kr.voiceparking.search.domain.ParkingLot;
import kr.voiceparking.search.domain.ParkingPass;
import kr.voiceparking.search.domain.ParsedQuery;
import kr.voiceparking.search.domain.ScoredPass;

/** 후보를 모으고 점수를 환산해 상위 3개를 고른다 (BR2.1~BR2.3, BR3.1~BR3.5). */
public class RecommendationService {

    static final int PRICE_WEIGHT = 40;
    static final int DISTANCE_WEIGHT = 40;
    static final int TIME_FIT_WEIGHT = 20;
    private static final int MAX_RECOMMENDATIONS = 3;

    private static final Comparator<ScoredPass> ORDER = Comparator
            .comparingInt(ScoredPass::totalScore).reversed()
            .thenComparingInt(ScoredPass::totalPrice)
            .thenComparingInt((ScoredPass s) -> s.lot().distanceMeters())
            .thenComparing(s -> s.pass().passId());

    private final ParkingRepository repository;

    public RecommendationService(ParkingRepository repository) {
        this.repository = repository;
    }

    /** 최적 1개(첫째)와 비교 후보 2개. 후보가 모자라면 있는 만큼. */
    public List<ScoredPass> recommend(ParsedQuery query) {
        List<ScoredPass> ranked = rankAll(query);
        return ranked.subList(0, Math.min(MAX_RECOMMENDATIONS, ranked.size()));
    }

    /** 시간을 채우는 모든 후보를 정렬해 돌려준다. */
    List<ScoredPass> rankAll(ParsedQuery query) {
        List<Candidate> candidates = new ArrayList<>();
        for (ParkingPass pass : repository.findPassesByPlace(query.placeName())) {
            if (pass.availableHours() < query.durationHours()) {
                continue; // BR2.2
            }
            ParkingLot lot = repository.findLot(pass.lotId()).orElseThrow();
            candidates.add(new Candidate(lot, pass, pass.totalPrice(query.durationHours()),
                    pass.availableHours() - query.durationHours()));
        }
        if (candidates.isEmpty()) {
            return List.of(); // BR2.3
        }

        Range price = Range.of(candidates, Candidate::totalPrice);
        Range distance = Range.of(candidates, c -> c.lot().distanceMeters());
        Range timeFit = Range.of(candidates, Candidate::hoursToSpare);

        List<ScoredPass> scored = new ArrayList<>();
        for (Candidate c : candidates) {
            Ratio priceScore = price.score(c.totalPrice());
            Ratio distanceScore = distance.score(c.lot().distanceMeters());
            Ratio timeFitScore = timeFit.score(c.hoursToSpare());

            Ratio priceShare = priceScore.timesPercent(PRICE_WEIGHT);
            Ratio distanceShare = distanceScore.timesPercent(DISTANCE_WEIGHT);
            Ratio timeFitShare = timeFitScore.timesPercent(TIME_FIT_WEIGHT);
            Ratio total = Ratio.ZERO.plus(priceShare).plus(distanceShare).plus(timeFitShare);

            scored.add(new ScoredPass(c.lot(), c.pass(), c.totalPrice(),
                    priceScore.roundHalfUp(), distanceScore.roundHalfUp(), timeFitScore.roundHalfUp(),
                    total.roundHalfUp(), topFactor(priceShare, distanceShare, timeFitShare)));
        }
        scored.sort(ORDER);
        return List.copyOf(scored);
    }

    /** 기여도가 가장 큰 항목. 같으면 PRICE, DISTANCE, TIME_FIT 순 (BR3.5). */
    private static Factor topFactor(Ratio price, Ratio distance, Ratio timeFit) {
        Factor top = Factor.PRICE;
        Ratio best = price;
        if (distance.compareTo(best) > 0) {
            top = Factor.DISTANCE;
            best = distance;
        }
        if (timeFit.compareTo(best) > 0) {
            top = Factor.TIME_FIT;
        }
        return top;
    }

    private record Candidate(ParkingLot lot, ParkingPass pass, int totalPrice, int hoursToSpare) {
    }

    /** 후보 전체의 최솟값·최댓값. 낮을수록 고득점인 min-max 환산 (BR3.1). */
    private record Range(int min, int max) {

        static Range of(List<Candidate> candidates, ToIntFunction<Candidate> value) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (Candidate c : candidates) {
                int v = value.applyAsInt(c);
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            return new Range(min, max);
        }

        Ratio score(int value) {
            if (max == min) {
                return new Ratio(100, 1);
            }
            return new Ratio(100L * (max - value), max - min);
        }
    }
}

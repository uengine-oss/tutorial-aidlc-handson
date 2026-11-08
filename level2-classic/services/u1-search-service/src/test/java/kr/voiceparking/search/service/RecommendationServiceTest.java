package kr.voiceparking.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kr.voiceparking.search.data.MockDataLoader;
import kr.voiceparking.search.data.ParkingData;
import kr.voiceparking.search.data.ParkingRepository;
import kr.voiceparking.search.domain.Factor;
import kr.voiceparking.search.domain.ParkingLot;
import kr.voiceparking.search.domain.ParkingPass;
import kr.voiceparking.search.domain.ParsedQuery;
import kr.voiceparking.search.domain.ScoredPass;

class RecommendationServiceTest {

    private RecommendationService bundled;

    @BeforeEach
    void setUp() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/data/parking-data.json")) {
            bundled = new RecommendationService(new ParkingRepository(new MockDataLoader().load(in)));
        }
    }

    /** 장소 P에 주차권을 (id, 거리, 시간당 요금, 최대 시간) 순서로 하나씩 가진 주차장들을 만든다. */
    private static RecommendationService serviceWith(Object[]... rows) {
        List<ParkingLot> lots = new java.util.ArrayList<>();
        List<ParkingPass> passes = new java.util.ArrayList<>();
        for (Object[] r : rows) {
            String id = (String) r[0];
            lots.add(new ParkingLot(id, "lot-" + id, "P", (Integer) r[1]));
            passes.add(new ParkingPass(id + "-1", id, (Integer) r[2], (Integer) r[3]));
        }
        return new RecommendationService(new ParkingRepository(new ParkingData(lots, passes)));
    }

    @Test
    void ranksTheDocumentedExampleAs62_60_50() {
        List<ScoredPass> all = bundled.rankAll(new ParsedQuery("강남역", 2));

        assertThat(all).extracting(s -> s.pass().passId())
                .containsExactly("gangnam-public-1", "yeoksam-bldg-1", "teheran-1", "sinnonhyeon-1");
        assertThat(all).extracting(ScoredPass::totalScore).containsExactly(62, 60, 50, 40);

        ScoredPass best = all.get(0);
        assertThat(best.totalPrice()).isEqualTo(6000);
        assertThat(best.priceScore()).isEqualTo(29);
        assertThat(best.distanceScore()).isEqualTo(89);
        assertThat(best.timeFitScore()).isEqualTo(75);
        assertThat(best.topFactor()).isEqualTo(Factor.DISTANCE);
    }

    @Test
    void excludesPassesThatCannotCoverTheRequestedHours() {
        List<ScoredPass> all = bundled.rankAll(new ParsedQuery("강남역", 2));

        assertThat(all).extracting(s -> s.pass().passId()).doesNotContain("gangnam-private-1");
    }

    @Test
    void returnsAtMostThreeRecommendations() {
        assertThat(bundled.recommend(new ParsedQuery("강남역", 2)))
                .extracting(s -> s.pass().passId())
                .containsExactly("gangnam-public-1", "yeoksam-bldg-1", "teheran-1");
    }

    @Test
    void returnsOnlyWhatExistsWhenFewerThanThreeCandidates() {
        assertThat(bundled.recommend(new ParsedQuery("홍대입구역", 2))).hasSize(2);
    }

    @Test
    void returnsEmptyWhenNoCandidateCoversTheDuration() {
        assertThat(bundled.recommend(new ParsedQuery("홍대입구역", 5))).isEmpty();
    }

    @Test
    void returnsEmptyForUnregisteredPlace() {
        assertThat(bundled.recommend(new ParsedQuery("부산역", 2))).isEmpty();
    }

    @Test
    void combinesComponentScoresWithFortyFortyTwentyWeights() {
        // AC2.2.1: 가격 100/50/0, 거리 80/100/0, 시간 적합도 100/60/0 -> 92/72/0
        RecommendationService service = serviceWith(
                new Object[] {"c1", 200, 1000, 1},
                new Object[] {"c2", 100, 1500, 3},
                new Object[] {"c3", 600, 2000, 6});

        List<ScoredPass> ranked = service.recommend(new ParsedQuery("P", 1));

        assertThat(ranked).extracting(s -> s.pass().passId()).containsExactly("c1-1", "c2-1", "c3-1");
        assertThat(ranked).extracting(ScoredPass::totalScore).containsExactly(92, 72, 0);
        assertThat(ranked.get(1).priceScore()).isEqualTo(50);
        assertThat(ranked.get(1).distanceScore()).isEqualTo(100);
        assertThat(ranked.get(1).timeFitScore()).isEqualTo(60);
    }

    @Test
    void singleCandidateScoresHundredInEveryItem() {
        ScoredPass single = serviceWith(new Object[] {"solo", 50, 4000, 3})
                .recommend(new ParsedQuery("P", 2)).get(0);
        assertThat(single.priceScore()).isEqualTo(100);
        assertThat(single.distanceScore()).isEqualTo(100);
        assertThat(single.timeFitScore()).isEqualTo(100);
        assertThat(single.totalScore()).isEqualTo(100);
        assertThat(single.topFactor()).isEqualTo(Factor.PRICE);
    }

    @Test
    void tieOnScoreGoesToTheLowerPrice() {
        // 두 후보 모두 종합 60점: 하나는 거리, 다른 하나는 가격이 강점. 가격이 낮은 쪽이 먼저.
        RecommendationService service = serviceWith(
                new Object[] {"near", 100, 2000, 1},
                new Object[] {"cheap", 200, 1000, 1});

        List<ScoredPass> ranked = service.recommend(new ParsedQuery("P", 1));

        assertThat(ranked).extracting(ScoredPass::totalScore).containsExactly(60, 60);
        assertThat(ranked).extracting(s -> s.pass().passId()).containsExactly("cheap-1", "near-1");
    }

    @Test
    void fullTieFallsBackToPassIdOrder() {
        RecommendationService service = serviceWith(
                new Object[] {"b", 100, 1000, 1},
                new Object[] {"a", 100, 1000, 1});

        assertThat(service.recommend(new ParsedQuery("P", 1)))
                .extracting(s -> s.pass().passId()).containsExactly("a-1", "b-1");
    }

    @Test
    void topFactorIsTheLargestContribution() {
        // c1: 가격 0 / 거리 0 / 시간 100 -> TIME_FIT. c2: 가격 100 / 거리 100 / 시간 0 -> PRICE(동률은 가격 우선)
        RecommendationService service = serviceWith(
                new Object[] {"c1", 300, 3000, 1},
                new Object[] {"c2", 100, 1000, 5});

        List<ScoredPass> ranked = service.rankAll(new ParsedQuery("P", 1));

        ScoredPass c1 = ranked.stream().filter(s -> s.pass().passId().equals("c1-1")).findFirst().orElseThrow();
        ScoredPass c2 = ranked.stream().filter(s -> s.pass().passId().equals("c2-1")).findFirst().orElseThrow();
        assertThat(c1.topFactor()).isEqualTo(Factor.TIME_FIT);
        assertThat(c2.topFactor()).isEqualTo(Factor.PRICE);
    }

    @Test
    void reasonTextNamesTheTopFactorAndScores() {
        ScoredPass best = bundled.recommend(new ParsedQuery("강남역", 2)).get(0);

        assertThat(new ReasonFormatter().format(best))
                .isEqualTo("강남역 공영주차장: 거리이(가) 가장 큰 강점입니다 (가격 29점, 거리 89점, 시간 적합도 75점)");
    }
}

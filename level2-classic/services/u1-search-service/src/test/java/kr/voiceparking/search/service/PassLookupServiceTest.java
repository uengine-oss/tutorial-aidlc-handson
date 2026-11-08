package kr.voiceparking.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kr.voiceparking.search.data.MockDataLoader;
import kr.voiceparking.search.data.ParkingRepository;
import kr.voiceparking.search.domain.ParsedQuery;

class PassLookupServiceTest {

    private PassLookupService lookup;
    private RecommendationService recommendation;

    @BeforeEach
    void setUp() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/data/parking-data.json")) {
            ParkingRepository repository = new ParkingRepository(new MockDataLoader().load(in));
            lookup = new PassLookupService(repository);
            recommendation = new RecommendationService(repository);
        }
    }

    private void assertRejected(String passId, int hours, ErrorCode expected) {
        assertThatThrownBy(() -> lookup.lookup(passId, hours))
                .isInstanceOfSatisfying(SearchException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(expected));
    }

    @Test
    void priceIsHourlyPriceTimesDuration() {
        PassInfo info = lookup.lookup("gangnam-public-1", 2);

        assertThat(info.passId()).isEqualTo("gangnam-public-1");
        assertThat(info.lotName()).isEqualTo("강남역 공영주차장");
        assertThat(info.durationHours()).isEqualTo(2);
        assertThat(info.price()).isEqualTo(6000);
    }

    @Test
    void priceMatchesTheRecommendationPrice() {
        int recommended = recommendation.recommend(new ParsedQuery("강남역", 2)).get(0).totalPrice();

        assertThat(lookup.lookup("gangnam-public-1", 2).price()).isEqualTo(recommended);
    }

    @Test
    void unknownPassIsNotFound() {
        assertRejected("nope", 2, ErrorCode.PASS_NOT_FOUND);
    }

    @Test
    void passThatCannotCoverTheDurationIsUnprocessable() {
        assertRejected("gangnam-private-1", 2, ErrorCode.DURATION_NOT_AVAILABLE);
    }

    @Test
    void durationOutsideOneToTwentyFourIsInvalid() {
        assertRejected("gangnam-public-1", 0, ErrorCode.INVALID_DURATION);
        assertRejected("gangnam-public-1", 25, ErrorCode.INVALID_DURATION);
    }

    @Test
    void durationEqualToAvailableHoursIsAccepted() {
        assertThat(lookup.lookup("gangnam-public-1", 3).price()).isEqualTo(9000);
    }
}

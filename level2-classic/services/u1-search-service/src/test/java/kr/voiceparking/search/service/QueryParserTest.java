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

class QueryParserTest {

    private QueryParser parser;

    @BeforeEach
    void setUp() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/data/parking-data.json")) {
            parser = new QueryParser(new ParkingRepository(new MockDataLoader().load(in)));
        }
    }

    private void assertRejected(String text, ErrorCode expected) {
        assertThatThrownBy(() -> parser.parse(text))
                .isInstanceOfSatisfying(SearchException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(expected));
    }

    @Test
    void extractsPlaceAndDigitDuration() {
        assertThat(parser.parse("강남역 근처 2시간 주차권 찾아줘"))
                .isEqualTo(new ParsedQuery("강남역", 2));
    }

    @Test
    void extractsKoreanNumberWordDuration() {
        assertThat(parser.parse("홍대입구역에서 세 시간만 세울래")).isEqualTo(new ParsedQuery("홍대입구역", 3));
        assertThat(parser.parse("강남역 한 시간")).isEqualTo(new ParsedQuery("강남역", 1));
    }

    @Test
    void twoDigitDurationIsNotReadAsOneDigit() {
        assertThat(parser.parse("강남역 12시간").durationHours()).isEqualTo(12);
    }

    @Test
    void firstPlaceAndFirstDurationWin() {
        assertThat(parser.parse("홍대입구역 말고 강남역 두 시간 아니 세 시간"))
                .isEqualTo(new ParsedQuery("홍대입구역", 2));
    }

    @Test
    void missingPlaceIsRejected() {
        assertRejected("2시간 주차권 찾아줘", ErrorCode.MISSING_PLACE);
    }

    @Test
    void missingPlaceWinsWhenBothAreMissing() {
        assertRejected("주차권 찾아줘", ErrorCode.MISSING_PLACE);
    }

    @Test
    void missingDurationIsRejected() {
        assertRejected("강남역 근처 주차권 찾아줘", ErrorCode.MISSING_DURATION);
        assertRejected("강남역 30분만", ErrorCode.MISSING_DURATION);
    }

    @Test
    void outOfRangeDurationIsTreatedAsMissing() {
        assertRejected("강남역 0시간", ErrorCode.MISSING_DURATION);
        assertRejected("강남역 25시간", ErrorCode.MISSING_DURATION);
        assertRejected("강남역 99999999999시간", ErrorCode.MISSING_DURATION);
    }

    @Test
    void boundaryDurationsAreAccepted() {
        assertThat(parser.parse("강남역 1시간").durationHours()).isEqualTo(1);
        assertThat(parser.parse("강남역 24시간").durationHours()).isEqualTo(24);
    }
}

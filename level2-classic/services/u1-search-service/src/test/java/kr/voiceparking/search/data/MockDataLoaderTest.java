package kr.voiceparking.search.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import kr.voiceparking.search.domain.ParkingLot;
import kr.voiceparking.search.domain.ParkingPass;

class MockDataLoaderTest {

    private final MockDataLoader loader = new MockDataLoader();

    private static InputStream json(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void loadsTheSevenMockPassesFromTheBundledFile() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/data/parking-data.json")) {
            ParkingData data = loader.load(in);

            assertThat(data.lots()).hasSize(7);
            assertThat(data.passes()).hasSize(7);
            ParkingLot gangnam = data.lots().stream()
                    .filter(l -> l.lotId().equals("gangnam-public")).findFirst().orElseThrow();
            assertThat(gangnam.name()).isEqualTo("강남역 공영주차장");
            assertThat(gangnam.placeName()).isEqualTo("강남역");
            assertThat(gangnam.distanceMeters()).isEqualTo(120);
            ParkingPass pass = data.passes().stream()
                    .filter(p -> p.passId().equals("gangnam-public-1")).findFirst().orElseThrow();
            assertThat(pass.lotId()).isEqualTo("gangnam-public");
            assertThat(pass.hourlyPrice()).isEqualTo(3000);
            assertThat(pass.availableHours()).isEqualTo(3);
        }
    }

    @Test
    void refusesPassThatReferencesUnknownLot() {
        String body = """
                {"lots":[{"lotId":"a","name":"A","placeName":"P","distanceMeters":10}],
                 "passes":[{"passId":"a-1","lotId":"missing","hourlyPrice":1000,"availableHours":2}]}
                """;
        assertThatThrownBy(() -> loader.load(json(body)))
                .isInstanceOf(DataLoadException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void refusesDuplicateLotId() {
        String body = """
                {"lots":[{"lotId":"a","name":"A","placeName":"P","distanceMeters":10},
                         {"lotId":"a","name":"B","placeName":"P","distanceMeters":20}],
                 "passes":[{"passId":"a-1","lotId":"a","hourlyPrice":1000,"availableHours":2}]}
                """;
        assertThatThrownBy(() -> loader.load(json(body)))
                .isInstanceOf(DataLoadException.class)
                .hasMessageContaining("lotId");
    }

    @Test
    void refusesDuplicatePassId() {
        String body = """
                {"lots":[{"lotId":"a","name":"A","placeName":"P","distanceMeters":10}],
                 "passes":[{"passId":"a-1","lotId":"a","hourlyPrice":1000,"availableHours":2},
                           {"passId":"a-1","lotId":"a","hourlyPrice":1500,"availableHours":3}]}
                """;
        assertThatThrownBy(() -> loader.load(json(body)))
                .isInstanceOf(DataLoadException.class)
                .hasMessageContaining("passId");
    }

    @Test
    void refusesMissingRequiredField() {
        String body = """
                {"lots":[{"lotId":"a","placeName":"P","distanceMeters":10}],
                 "passes":[{"passId":"a-1","lotId":"a","hourlyPrice":1000,"availableHours":2}]}
                """;
        assertThatThrownBy(() -> loader.load(json(body)))
                .isInstanceOf(DataLoadException.class)
                .hasMessageContaining("name");
    }

    @Test
    void refusesOutOfRangeNumbers() {
        String body = """
                {"lots":[{"lotId":"a","name":"A","placeName":"P","distanceMeters":10}],
                 "passes":[{"passId":"a-1","lotId":"a","hourlyPrice":0,"availableHours":2}]}
                """;
        assertThatThrownBy(() -> loader.load(json(body)))
                .isInstanceOf(DataLoadException.class)
                .hasMessageContaining("hourlyPrice");
    }

    @Test
    void refusesMalformedJson() {
        assertThatThrownBy(() -> loader.load(json("{ not json")))
                .isInstanceOf(DataLoadException.class);
    }
}

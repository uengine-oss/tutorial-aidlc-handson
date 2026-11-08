package kr.voiceparking.search.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kr.voiceparking.search.domain.ParkingLot;
import kr.voiceparking.search.domain.ParkingPass;

class ParkingRepositoryTest {

    private ParkingRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/data/parking-data.json")) {
            repository = new ParkingRepository(new MockDataLoader().load(in));
        }
    }

    @Test
    void returnsPassesOfTheRequestedPlaceOnly() {
        assertThat(repository.findPassesByPlace("강남역"))
                .extracting(ParkingPass::passId)
                .containsExactlyInAnyOrder("gangnam-public-1", "yeoksam-bldg-1", "teheran-1",
                        "gangnam-private-1", "sinnonhyeon-1");
        assertThat(repository.findPassesByPlace("홍대입구역"))
                .extracting(ParkingPass::passId)
                .containsExactlyInAnyOrder("hongdae-public-1", "hongdae-bldg-1");
    }

    @Test
    void returnsEmptyForUnknownPlace() {
        assertThat(repository.findPassesByPlace("부산역")).isEmpty();
    }

    @Test
    void findsPassAndLotById() {
        assertThat(repository.findPass("teheran-1")).hasValueSatisfying(p -> {
            assertThat(p.hourlyPrice()).isEqualTo(2500);
            assertThat(p.availableHours()).isEqualTo(4);
        });
        assertThat(repository.findLot("teheran")).map(ParkingLot::name).contains("테헤란로 주차장");
    }

    @Test
    void returnsEmptyForUnknownPassId() {
        assertThat(repository.findPass("nope")).isEmpty();
        assertThat(repository.findLot("nope")).isEmpty();
    }

    @Test
    void exposesRegisteredPlaceNames() {
        assertThat(repository.placeNames()).containsExactlyInAnyOrder("강남역", "홍대입구역");
    }
}

package kr.voiceparking.search.data;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/** 기동할 때 목업 데이터를 적재한다. 검증에 실패하면 예외로 기동이 중단된다. */
@Configuration
public class DataConfig {

    private static final Logger log = LoggerFactory.getLogger(DataConfig.class);

    @Bean
    public ParkingRepository parkingRepository(@Value("${parking.data.location}") Resource location) {
        try (InputStream in = location.getInputStream()) {
            ParkingData data = new MockDataLoader().load(in);
            log.info("mock data loaded lots={} passes={}", data.lots().size(), data.passes().size());
            return new ParkingRepository(data);
        } catch (DataLoadException e) {
            log.error("mock data rejected reason={}", e.getMessage());
            throw e;
        } catch (IOException e) {
            log.error("mock data unreadable location={}", location);
            throw new DataLoadException("목업 데이터 파일을 읽을 수 없습니다", e);
        }
    }
}

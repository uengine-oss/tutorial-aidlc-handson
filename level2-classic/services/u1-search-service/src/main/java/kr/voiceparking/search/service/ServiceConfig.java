package kr.voiceparking.search.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import kr.voiceparking.search.data.ParkingRepository;

/** 상태 없는 서비스 객체를 빈으로 등록한다. */
@Configuration
public class ServiceConfig {

    @Bean
    public QueryParser queryParser(ParkingRepository repository) {
        return new QueryParser(repository);
    }

    @Bean
    public RecommendationService recommendationService(ParkingRepository repository) {
        return new RecommendationService(repository);
    }

    @Bean
    public PassLookupService passLookupService(ParkingRepository repository) {
        return new PassLookupService(repository);
    }

    @Bean
    public ReasonFormatter reasonFormatter() {
        return new ReasonFormatter();
    }
}

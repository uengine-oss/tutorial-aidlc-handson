package com.voiceparking.recommendation.config;

import com.voiceparking.recommendation.repository.TicketRepository;
import com.voiceparking.recommendation.service.ExplanationGenerator;
import com.voiceparking.recommendation.service.QueryParser;
import com.voiceparking.recommendation.service.RecommendationService;
import com.voiceparking.recommendation.service.ScoreCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceConfig {

    @Bean
    public RecommendationService recommendationService(
            TicketRepository repository,
            @Value("${recommendation.radius-meters:1000}") int radiusMeters) {
        return new RecommendationService(repository, new QueryParser(repository),
                new ScoreCalculator(), new ExplanationGenerator(), radiusMeters);
    }
}

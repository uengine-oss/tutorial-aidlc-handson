package kr.voiceparking.reservation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import kr.voiceparking.reservation.reservation.HttpSearchClient;
import kr.voiceparking.reservation.reservation.SearchClient;

/** u1 주소는 환경 변수 SEARCH_SERVICE_URL로 주입하고 기본값만 설정 파일에 둔다 (NFR8.1). */
@Configuration
public class SearchClientConfig {

    @Bean
    public SearchClient searchClient(@Value("${search.service.url}") String searchServiceUrl) {
        return new HttpSearchClient(searchServiceUrl);
    }
}

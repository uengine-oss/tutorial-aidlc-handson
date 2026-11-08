package kr.voiceparking.reservation.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 실제 u1 호출 대신 가짜 SearchClient를 쓰는 시험 설정. */
@TestConfiguration
public class FakeSearchConfig {

    @Bean
    @Primary
    public FakeSearchClient fakeSearchClient() {
        return new FakeSearchClient();
    }
}

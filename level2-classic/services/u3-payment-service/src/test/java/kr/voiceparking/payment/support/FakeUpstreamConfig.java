package kr.voiceparking.payment.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 웹 계층 시험에서 u2 호출과 모의 PG를 가짜로 바꾼다. 실제 빈보다 우선한다. */
@TestConfiguration
public class FakeUpstreamConfig {

    @Bean
    @Primary
    public FakeReservationClient fakeReservationClient() {
        return new FakeReservationClient();
    }

    @Bean
    @Primary
    public RecordingGateway recordingGateway() {
        return new RecordingGateway();
    }
}

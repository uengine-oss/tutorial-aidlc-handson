package kr.voiceparking.reservation.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 설정값. 결제 요청 시점부터 자동 취소까지의 시간(기본 180초)과 푸시 설정.
 * 시험은 마감을 짧게 줄여 실제 스케줄러 경로를 확인한다 (NFR2.4).
 */
@ConfigurationProperties("reservation")
public record ReservationProperties(@DefaultValue("180s") Duration paymentTimeout, @DefaultValue Push push) {

    /** 푸시 설정: 동시 구독 수 상한, 연결별 쓰기 큐 상한, 쓰기 하나의 상한, 연결 유지 신호 간격, SSE 시간 초과. */
    public record Push(@DefaultValue("32") int maxSubscriptions, @DefaultValue("16") int queueLimit,
            @DefaultValue("1s") Duration writeTimeout, @DefaultValue("15s") Duration keepAliveInterval,
            @DefaultValue("10m") Duration emitterTimeout) {

        public static Push defaults() {
            return new Push(32, 16, Duration.ofSeconds(1), Duration.ofSeconds(15), Duration.ofMinutes(10));
        }
    }

    public static ReservationProperties defaults() {
        return new ReservationProperties(Duration.ofSeconds(180), Push.defaults());
    }

    public ReservationProperties withPaymentTimeout(Duration timeout) {
        return new ReservationProperties(timeout, push);
    }
}

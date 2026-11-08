package kr.voiceparking.payment.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import kr.voiceparking.payment.payment.HttpReservationClient;
import kr.voiceparking.payment.payment.PaymentGateway;
import kr.voiceparking.payment.payment.PaymentService;
import kr.voiceparking.payment.payment.PaymentStore;
import kr.voiceparking.payment.payment.ReservationClient;

/** u2 주소는 환경 변수 RESERVATION_SERVICE_URL로 주입하고 기본값만 설정 파일에 둔다 (NFR8.1). */
@Configuration
public class ReservationClientConfig {

    @Bean
    public ReservationClient reservationClient(@Value("${reservation.service.url}") String reservationServiceUrl) {
        return new HttpReservationClient(reservationServiceUrl);
    }

    @Bean
    public PaymentService paymentService(PaymentStore store, ReservationClient reservations, PaymentGateway gateway,
            Clock clock) {
        // 잠금 대기 상한(7초)은 설정으로 노출하지 않는다: 설계가 정한 값이다 (E3).
        return new PaymentService(store, reservations, gateway, clock, PaymentService.DEFAULT_LOCK_WAIT);
    }
}

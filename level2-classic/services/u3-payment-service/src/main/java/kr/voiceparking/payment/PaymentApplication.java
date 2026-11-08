package kr.voiceparking.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** VoiceParking 결제 서비스 (U3). 결제 요청, 모의 PG, 서명된 웹훅 처리. */
@SpringBootApplication
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}

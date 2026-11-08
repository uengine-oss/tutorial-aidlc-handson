package kr.voiceparking.payment.config;

import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import kr.voiceparking.payment.payment.PaymentGateway;
import kr.voiceparking.payment.pg.MockPaymentGateway;
import kr.voiceparking.payment.pg.WebhookCaller;
import kr.voiceparking.payment.signature.HmacSigner;

/**
 * 서명 비밀 값과 모의 PG를 엮는 유일한 곳이다. {@code pg} 패키지를 아는 것은 이 설정뿐이라, 실제 PG로 바꿀 때는 이
 * 파일과 {@code pg} 패키지만 걷어내면 된다 (BR2.2).
 *
 * <p>비밀 값은 환경 변수 PAYMENT_WEBHOOK_SECRET에서만 온다. 기본값이 없어 설정이 없으면 값을 풀지 못해 기동하지
 * 않고, 비어 있으면 {@link HmacSigner}가 거부한다 (BR3.2).
 */
@Configuration
public class PgConfig {

    @Bean
    public HmacSigner hmacSigner(@Value("${payment.webhook.secret}") String secret) {
        return new HmacSigner(secret);
    }

    @Bean
    public WebhookCaller webhookCaller(@Value("${payment.webhook.url}") String webhookUrl) {
        return new WebhookCaller(webhookUrl, WebhookCaller.DEFAULT_TIMEOUT);
    }

    @Bean
    public PaymentGateway mockPaymentGateway(Clock clock, @Qualifier("pgScheduler") ThreadPoolTaskScheduler scheduler,
            @Qualifier("pgDispatchExecutor") ThreadPoolExecutor dispatch, WebhookCaller caller, HmacSigner signer) {
        return new MockPaymentGateway(clock, scheduler, dispatch, caller, signer);
    }
}

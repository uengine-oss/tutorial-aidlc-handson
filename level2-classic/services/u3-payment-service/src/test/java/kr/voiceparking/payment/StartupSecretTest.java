package kr.voiceparking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** BR3.2: 웹훅 서명 비밀 값이 없거나 비어 있으면 서비스가 기동하지 않는다. */
class StartupSecretTest {

    private static SpringApplicationBuilder app() {
        return new SpringApplicationBuilder(PaymentApplication.class)
                .properties("spring.main.web-application-type=none", "spring.main.banner-mode=off");
    }

    @Test
    void aBlankSecretRefusesToStart() {
        assertThatThrownBy(() -> app().run("--payment.webhook.secret=")).hasRootCauseInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    void aWhitespaceOnlySecretRefusesToStart() {
        assertThatThrownBy(() -> app().run("--payment.webhook.secret=   ")).hasRootCauseInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    void aMissingSecretRefusesToStartBecauseTheSettingHasNoDefault() {
        assumeTrue(System.getenv("PAYMENT_WEBHOOK_SECRET") == null, "환경 변수가 이미 설정되어 있어 건너뜀");

        assertThatThrownBy(() -> app().run("--spring.config.location=classpath:/application.properties"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause().hasMessageContaining("PAYMENT_WEBHOOK_SECRET");
    }

    @Test
    void aProvidedSecretStarts() {
        try (ConfigurableApplicationContext context = app().run("--payment.webhook.secret=some-injected-value")) {
            assertThat(context.isRunning()).isTrue();
        }
    }
}

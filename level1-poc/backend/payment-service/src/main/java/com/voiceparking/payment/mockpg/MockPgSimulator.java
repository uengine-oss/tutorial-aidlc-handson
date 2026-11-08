package com.voiceparking.payment.mockpg;

import com.voiceparking.payment.service.WebhookResult;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 시연용 모의 PG. 요청을 받으면 잠시 뒤 비동기로 웹훅을 보낸다. */
@Component
public class MockPgSimulator {

    private static final Logger log = LoggerFactory.getLogger(MockPgSimulator.class);

    private final WebhookSender sender;
    private final long delayMs;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    public MockPgSimulator(WebhookSender sender, @Value("${payment.mock-pg-delay-ms:300}") long delayMs) {
        this.sender = sender;
        this.delayMs = delayMs;
    }

    public void scheduleWebhook(String paymentId, WebhookResult result, String reason) {
        executor.schedule(() -> {
            try {
                sender.send(paymentId, result, reason);
            } catch (RuntimeException e) {
                // PG라면 재시도하겠지만 시연용이므로 원인을 남기고 끝낸다.
                log.error("모의 PG 웹훅 전송 실패 paymentId={}", paymentId, e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}

package com.voiceparking.payment.mockpg;

import com.voiceparking.payment.service.WebhookResult;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 실제 HTTP로 웹훅 URL을 호출한다(자기 서비스의 /api/payments/webhook). */
@Component
public class HttpWebhookSender implements WebhookSender {

    private final RestClient client;
    private final String webhookUrl;

    public HttpWebhookSender(@Value("${payment.webhook-url}") String webhookUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.client = RestClient.builder().requestFactory(factory).build();
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void send(String paymentId, WebhookResult result, String reason) {
        Map<String, String> body = reason == null
                ? Map.of("paymentId", paymentId, "result", result.name())
                : Map.of("paymentId", paymentId, "result", result.name(), "reason", reason);
        client.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toBodilessEntity();
    }
}

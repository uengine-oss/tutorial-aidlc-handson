package kr.voiceparking.payment.support;

import java.nio.charset.StandardCharsets;

import kr.voiceparking.payment.signature.HmacSigner;

/** 시험이 웹훅 본문에 서명할 때 쓰는 도우미. 시험용 값은 src/test/resources/config/application.properties와 같다. */
public final class Signing {

    public static final String TEST_SECRET = "test-only-webhook-signing-value";

    private static final HmacSigner SIGNER = new HmacSigner(TEST_SECRET);

    private Signing() {
    }

    public static String sign(String body) {
        return SIGNER.sign(body.getBytes(StandardCharsets.UTF_8));
    }

    public static String webhookBody(String eventId, String paymentId, String outcome) {
        return "{\"eventId\":\"" + eventId + "\",\"paymentId\":\"" + paymentId + "\",\"outcome\":\"" + outcome
                + "\",\"occurredAt\":\"2026-11-05T01:00:05Z\"}";
    }
}

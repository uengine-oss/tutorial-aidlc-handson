package kr.voiceparking.payment.web;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import kr.voiceparking.payment.payment.ErrorCode;
import kr.voiceparking.payment.payment.Handled;
import kr.voiceparking.payment.payment.Outcome;
import kr.voiceparking.payment.payment.PaymentException;
import kr.voiceparking.payment.payment.PaymentService;
import kr.voiceparking.payment.payment.WebhookCall;
import kr.voiceparking.payment.web.dto.WebhookResponse;

/**
 * C6 웹훅 수신. 순서는 크기 검사(400) → 서명(401) → 형식(400) → 알려진 결제(404, 서비스)다. 본문 원문 바이트는
 * {@link WebhookBodyFilter}가 8KB까지만 읽어 두었다. 서명 검증은 JSON 해석보다 앞에서 한다 (BR3.1).
 */
@RestController
public class WebhookController {

    private static final int MAX_ID_LENGTH = 100;

    private final PaymentService service;
    private final SignatureVerifier verifier;
    private final ObjectMapper mapper;

    public WebhookController(PaymentService service, SignatureVerifier verifier, ObjectMapper mapper) {
        this.service = service;
        this.verifier = verifier;
        this.mapper = mapper;
    }

    @PostMapping("/api/payments/webhook")
    public WebhookResponse receive(HttpServletRequest request,
            @RequestHeader(value = "X-Signature", required = false) String signature) {
        if (Boolean.TRUE.equals(request.getAttribute(WebhookBodyFilter.OVERSIZE_ATTRIBUTE))
                || !(request.getAttribute(WebhookBodyFilter.BODY_ATTRIBUTE) instanceof byte[])) {
            throw new PaymentException(ErrorCode.INVALID_REQUEST);
        }
        byte[] body = (byte[]) request.getAttribute(WebhookBodyFilter.BODY_ATTRIBUTE);
        verifier.verify(body, signature);
        Handled handled = service.handleWebhook(parse(body));
        return new WebhookResponse(handled.name());
    }

    /** eventId(1~100자), paymentId, outcome(APPROVED·FAILED), occurredAt(날짜 시각)을 검증한다 (BR3.3). */
    private WebhookCall parse(byte[] body) {
        JsonNode root;
        try {
            root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
        } catch (IOException e) {
            throw invalid();
        }
        if (root == null || !root.isObject()) {
            throw invalid();
        }
        String eventId = text(root, "eventId");
        String paymentId = text(root, "paymentId");
        if (eventId.length() > MAX_ID_LENGTH || paymentId.length() > MAX_ID_LENGTH) {
            throw invalid();
        }
        try {
            Outcome outcome = Outcome.valueOf(text(root, "outcome"));
            OffsetDateTime occurredAt = OffsetDateTime.parse(text(root, "occurredAt"));
            return new WebhookCall(eventId, paymentId, outcome, occurredAt.toInstant());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid();
        }
        return value.asText();
    }

    private static PaymentException invalid() {
        return new PaymentException(ErrorCode.INVALID_REQUEST);
    }
}

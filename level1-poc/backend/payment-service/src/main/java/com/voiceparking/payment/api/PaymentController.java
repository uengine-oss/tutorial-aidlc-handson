package com.voiceparking.payment.api;

import com.voiceparking.payment.domain.PaymentView;
import com.voiceparking.payment.mockpg.MockPgSimulator;
import com.voiceparking.payment.service.PaymentService;
import com.voiceparking.payment.service.WebhookResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    public record CreatePaymentRequest(
            @NotBlank(message = "reservationId는 필수입니다") String reservationId,
            @Min(value = 1, message = "amount는 1 이상이어야 합니다") int amount) {
    }

    public record WebhookRequest(
            @NotBlank(message = "paymentId는 필수입니다") String paymentId,
            @NotNull(message = "result는 APPROVED 또는 FAILED여야 합니다") WebhookResult result,
            String reason) {
    }

    private final PaymentService service;
    private final MockPgSimulator mockPg;

    public PaymentController(PaymentService service, MockPgSimulator mockPg) {
        this.service = service;
        this.mockPg = mockPg;
    }

    @PostMapping("/api/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView create(@Valid @RequestBody CreatePaymentRequest request) {
        return service.create(request.reservationId(), request.amount());
    }

    @GetMapping("/api/payments/{id}")
    public PaymentView get(@PathVariable String id) {
        return service.get(id);
    }

    /** PG가 호출하는 웹훅. 중복 호출에도 같은 결과를 돌려준다. */
    @PostMapping("/api/payments/webhook")
    public PaymentView webhook(@Valid @RequestBody WebhookRequest request) {
        return service.handleWebhook(request.paymentId(), request.result(), request.reason());
    }

    /** 시연용: 모의 PG에서 승인 웹훅을 비동기로 보내게 한다. */
    @PostMapping("/api/mock-pg/{paymentId}/approve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> mockApprove(@PathVariable String paymentId) {
        service.get(paymentId);
        mockPg.scheduleWebhook(paymentId, WebhookResult.APPROVED, null);
        return Map.of("accepted", true);
    }

    /** 시연용: 모의 PG에서 실패 웹훅을 비동기로 보내게 한다. */
    @PostMapping("/api/mock-pg/{paymentId}/fail")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> mockFail(@PathVariable String paymentId) {
        service.get(paymentId);
        mockPg.scheduleWebhook(paymentId, WebhookResult.FAILED, "카드 한도 초과(모의 PG 실패)");
        return Map.of("accepted", true);
    }
}

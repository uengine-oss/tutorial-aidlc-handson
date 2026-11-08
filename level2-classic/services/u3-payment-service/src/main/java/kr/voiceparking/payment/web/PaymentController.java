package kr.voiceparking.payment.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import kr.voiceparking.payment.payment.PaymentCommand;
import kr.voiceparking.payment.payment.PaymentReceipt;
import kr.voiceparking.payment.payment.PaymentService;
import kr.voiceparking.payment.web.dto.PaymentRequest;
import kr.voiceparking.payment.web.dto.PaymentResponse;

/** C2 결제 요청. 202(접수) 또는 200(이미 끝난 예약의 같은 키 재요청). 값의 검증과 판단은 서비스가 한다. */
@RestController
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/api/payments")
    public ResponseEntity<PaymentResponse> create(@RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestBody PaymentRequest body, HttpServletRequest request) {
        long received = request.getAttribute(RequestLogFilter.RECEIVED_ATTRIBUTE) instanceof Long nanos ? nanos
                : System.nanoTime();
        PaymentReceipt receipt = service.request(new PaymentCommand(sessionId, body.passId(), body.durationHours(),
                body.idempotencyKey(), body.mockScenario(), body.mockDelaySeconds()), received);
        HttpStatus status = receipt.accepted() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(PaymentResponse.of(receipt));
    }
}

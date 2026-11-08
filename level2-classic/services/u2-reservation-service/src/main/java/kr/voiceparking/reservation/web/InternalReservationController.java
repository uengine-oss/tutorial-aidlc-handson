package kr.voiceparking.reservation.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import kr.voiceparking.reservation.domain.AppliedEvent;
import kr.voiceparking.reservation.domain.Outcome;
import kr.voiceparking.reservation.reservation.CreateCommand;
import kr.voiceparking.reservation.reservation.ErrorCode;
import kr.voiceparking.reservation.reservation.ReservationException;
import kr.voiceparking.reservation.reservation.ReservationService;
import kr.voiceparking.reservation.reservation.ReservationService.CreateResult;
import kr.voiceparking.reservation.web.dto.CreateRequest;
import kr.voiceparking.reservation.web.dto.CreateResponse;
import kr.voiceparking.reservation.web.dto.PaymentResultRequest;
import kr.voiceparking.reservation.web.dto.PaymentResultResponse;

/** C5 내부 경로(u3 전용): 예약 생성과 결제 결과 반영. 화면 프록시에 연결하지 않고 루프백에만 연다 (NFR6.5). */
@RestController
public class InternalReservationController {

    private final ReservationService service;

    public InternalReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping("/internal/reservations")
    public ResponseEntity<CreateResponse> create(@RequestBody CreateRequest request) {
        CreateResult result = service.create(new CreateCommand(request.sessionId(), request.passId(),
                request.durationHours(), request.idempotencyKey()));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(CreateResponse.of(result.reservation()));
    }

    @PostMapping("/internal/reservations/{reservationId}/payment-result")
    public PaymentResultResponse applyPaymentResult(@PathVariable String reservationId,
            @RequestBody PaymentResultRequest request) {
        AppliedEvent event = service.applyPaymentResult(reservationId, request.eventId(), parse(request.outcome()));
        return new PaymentResultResponse(event.result().name(), event.reservationStatus().name());
    }

    /** APPROVED, FAILED 외의 값은 400이다 (D3). 없는 값은 서비스가 검증한다. */
    private static Outcome parse(String outcome) {
        if (outcome == null) {
            return null;
        }
        try {
            return Outcome.valueOf(outcome);
        } catch (IllegalArgumentException e) {
            throw new ReservationException(ErrorCode.INVALID_REQUEST);
        }
    }
}

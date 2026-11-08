package com.voiceparking.reservation.api;

import com.voiceparking.reservation.domain.ReservationView;
import com.voiceparking.reservation.event.SseHub;
import com.voiceparking.reservation.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class ReservationController {

    public record CreateReservationRequest(
            @NotBlank(message = "ticketId는 필수입니다") String ticketId,
            @NotBlank(message = "ticketName은 필수입니다") String ticketName,
            @Min(value = 1, message = "price는 1 이상이어야 합니다") int price) {
    }

    private final ReservationService service;
    private final SseHub hub;

    public ReservationController(ReservationService service, SseHub hub) {
        this.service = service;
        this.hub = hub;
    }

    @PostMapping("/api/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationView create(@Valid @RequestBody CreateReservationRequest request) {
        return service.create(request.ticketId(), request.ticketName(), request.price());
    }

    @GetMapping("/api/reservations/{id}")
    public ReservationView get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping(value = "/api/reservations/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id) {
        return hub.open(id, service.get(id));
    }

    /** 서비스 간 호출: 결제 서비스가 승인 후 예약 확정을 요청한다. */
    @PostMapping("/internal/reservations/{id}/confirm")
    public ReservationView confirm(@PathVariable String id) {
        return service.confirm(id);
    }
}

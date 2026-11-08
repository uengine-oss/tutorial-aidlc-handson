package kr.voiceparking.reservation.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import kr.voiceparking.reservation.notification.Snapshot;
import kr.voiceparking.reservation.reservation.ReservationService;

/** C3 예약 조회: 현재 예약과 단건. 세션은 X-Session-Id 헤더로만 받는다 (BR3.1~BR3.3). */
@RestController
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @GetMapping("/api/reservations/current")
    public Snapshot current(@RequestHeader(name = "X-Session-Id", required = false) String sessionHeader) {
        return service.current(SessionIds.require(sessionHeader)).toSnapshot();
    }

    @GetMapping("/api/reservations/{reservationId}")
    public Snapshot byId(@PathVariable String reservationId,
            @RequestHeader(name = "X-Session-Id", required = false) String sessionHeader) {
        return service.get(SessionIds.require(sessionHeader), reservationId).toSnapshot();
    }
}

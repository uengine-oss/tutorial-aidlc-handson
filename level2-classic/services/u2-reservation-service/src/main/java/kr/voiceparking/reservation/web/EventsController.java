package kr.voiceparking.reservation.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import kr.voiceparking.reservation.config.ReservationProperties;
import kr.voiceparking.reservation.domain.Reservation;
import kr.voiceparking.reservation.notification.NotificationService;
import kr.voiceparking.reservation.notification.SseChannel;
import kr.voiceparking.reservation.notification.Subscription;
import kr.voiceparking.reservation.reservation.ReservationService;

/**
 * C3 구독 (SSE). 세션은 헤더 또는 쿼리 sessionId에서 읽고 헤더가 우선한다. 소유 확인은 연결을 열기 전에 하고,
 * 알림이 보관한 스냅샷이 없을 때를 대비해 Reservation의 현재 상태로 만든 대체 스냅샷을 함께 넘긴다 (BR4.3).
 * 알림은 Reservation을 호출하지 않으므로 이 두 가지는 웹 계층이 잇는다.
 */
@RestController
public class EventsController {

    private final ReservationService reservations;
    private final NotificationService notifications;
    private final long emitterTimeoutMillis;

    public EventsController(ReservationService reservations, NotificationService notifications,
            ReservationProperties properties) {
        this.reservations = reservations;
        this.notifications = notifications;
        this.emitterTimeoutMillis = properties.push().emitterTimeout().toMillis();
    }

    @GetMapping("/api/reservations/{reservationId}/events")
    public SseEmitter subscribe(@PathVariable String reservationId,
            @RequestHeader(name = "X-Session-Id", required = false) String sessionHeader,
            @RequestParam(name = "sessionId", required = false) String sessionQuery) {
        String sessionId = SessionIds.resolve(sessionHeader, sessionQuery);
        Reservation reservation = reservations.get(sessionId, reservationId);

        SseEmitter emitter = new SseEmitter(emitterTimeoutMillis);
        SseChannel channel = new SseChannel(emitter);
        Subscription subscription = notifications.subscribe(reservationId, channel, reservation.toSnapshot());
        channel.bind(subscription);
        return emitter;
    }
}

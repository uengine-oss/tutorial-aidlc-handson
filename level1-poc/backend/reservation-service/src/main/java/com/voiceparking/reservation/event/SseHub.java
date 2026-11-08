package com.voiceparking.reservation.event;

import com.voiceparking.reservation.domain.ReservationView;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 예약 ID별 구독자(SSE 포함)에게 상태 변경을 즉시 밀어 준다 (FR4.2, NFR1). */
@Component
public class SseHub implements ReservationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);
    private static final long EMITTER_TIMEOUT_MS = 10 * 60 * 1000L;

    private final Map<String, List<Consumer<ReservationView>>> listeners = new ConcurrentHashMap<>();

    /** 일반 구독. 반환된 Runnable로 구독을 해제한다. */
    public Runnable subscribe(String reservationId, Consumer<ReservationView> listener) {
        List<Consumer<ReservationView>> list =
                listeners.computeIfAbsent(reservationId, k -> new CopyOnWriteArrayList<>());
        list.add(listener);
        return () -> list.remove(listener);
    }

    /** SSE 연결을 열고, 현재 상태를 즉시 한 번 보낸다(놓친 이벤트 방지). */
    public SseEmitter open(String reservationId, ReservationView current) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Consumer<ReservationView> listener = view -> send(emitter, view);
        Runnable unsubscribe = subscribe(reservationId, listener);
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> unsubscribe.run());
        listener.accept(current);
        return emitter;
    }

    @Override
    public void publish(ReservationView view) {
        List<Consumer<ReservationView>> list = listeners.get(view.id());
        if (list == null) {
            return;
        }
        for (Consumer<ReservationView> l : list) {
            try {
                l.accept(view);
            } catch (RuntimeException e) {
                log.warn("구독자 전달 실패, 구독을 제거합니다: {}", e.getMessage());
                list.remove(l);
            }
        }
    }

    private void send(SseEmitter emitter, ReservationView view) {
        try {
            emitter.send(SseEmitter.event().name("status").data(view));
            if (view.status().isFinal()) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException e) {
            // 클라이언트가 이미 끊긴 경우. 구독만 정리한다.
            emitter.completeWithError(e);
        }
    }
}

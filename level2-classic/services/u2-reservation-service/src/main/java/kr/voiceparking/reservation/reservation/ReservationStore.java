package kr.voiceparking.reservation.reservation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import kr.voiceparking.reservation.domain.AppliedEvent;
import kr.voiceparking.reservation.domain.Reservation;

/**
 * 인메모리 저장소. 예약, 멱등 키 색인, 세션별 목록, 판정 기록을 가진다 (BR5.3).
 * 예약 단위 잠금과 세션 단위 잠금을 키별로 따로 관리하며 전역 잠금은 없다 (BR5.2, NFR4.2).
 * 잠금 획득 순서는 세션 잠금, 예약 잠금이다. 역순으로 잡지 않는다.
 */
@Component
public class ReservationStore {

    private final Map<String, Reservation> byId = new ConcurrentHashMap<>();
    private final Map<String, String> idByKey = new ConcurrentHashMap<>();
    private final Map<String, List<String>> idsBySession = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AppliedEvent>> eventsByReservation = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> reservationLocks = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    /** 값을 통째로 바꿔 저장한다. 같은 reservationId가 이미 있으면 교체한다. */
    public void save(Reservation reservation) {
        Reservation previous = byId.put(reservation.reservationId(), reservation);
        if (previous == null) {
            idByKey.put(keyOf(reservation.sessionId(), reservation.idempotencyKey()), reservation.reservationId());
            idsBySession.computeIfAbsent(reservation.sessionId(), s -> new CopyOnWriteArrayList<>())
                    .add(reservation.reservationId());
        }
    }

    public Optional<Reservation> findById(String reservationId) {
        return Optional.ofNullable(byId.get(reservationId));
    }

    public Optional<Reservation> findByKey(String sessionId, String idempotencyKey) {
        String id = idByKey.get(keyOf(sessionId, idempotencyKey));
        return id == null ? Optional.empty() : findById(id);
    }

    /** 세션에서 결제 대기 상태인 예약. 제약상 최대 1건이다. */
    public Optional<Reservation> findPending(String sessionId) {
        return sessionReservations(sessionId).filter(Reservation::isPending).findFirst();
    }

    /** 결제 요청 시각이 가장 늦은 예약. 같으면 나중에 저장된 것 (BR3.3). */
    public Optional<Reservation> findLatest(String sessionId) {
        Reservation latest = null;
        for (String id : idsBySession.getOrDefault(sessionId, List.of())) {
            Reservation candidate = byId.get(id);
            if (candidate != null
                    && (latest == null || !candidate.paymentRequestedAt().isBefore(latest.paymentRequestedAt()))) {
                latest = candidate;
            }
        }
        return Optional.ofNullable(latest);
    }

    public Optional<AppliedEvent> findEvent(String reservationId, String eventId) {
        Map<String, AppliedEvent> events = eventsByReservation.get(reservationId);
        return events == null ? Optional.empty() : Optional.ofNullable(events.get(eventId));
    }

    /** 판정 기록을 남긴다. 같은 eventId가 이미 있으면 처음 기록을 유지한다. */
    public void recordEvent(String reservationId, AppliedEvent event) {
        eventsByReservation.computeIfAbsent(reservationId, id -> new ConcurrentHashMap<>())
                .putIfAbsent(event.eventId(), event);
    }

    public <T> T withReservationLock(String reservationId, Supplier<T> action) {
        return withLock(reservationLocks.computeIfAbsent(reservationId, id -> new ReentrantLock()), action);
    }

    public <T> T withSessionLock(String sessionId, Supplier<T> action) {
        return withLock(sessionLocks.computeIfAbsent(sessionId, id -> new ReentrantLock()), action);
    }

    private static <T> T withLock(ReentrantLock lock, Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private Stream<Reservation> sessionReservations(String sessionId) {
        return idsBySession.getOrDefault(sessionId, List.of()).stream()
                .map(byId::get)
                .filter(Objects::nonNull);
    }

    private static String keyOf(String sessionId, String idempotencyKey) {
        return sessionId + '\n' + idempotencyKey;
    }
}

package kr.voiceparking.reservation.reservation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import kr.voiceparking.reservation.config.ReservationProperties;
import kr.voiceparking.reservation.domain.AppliedEvent;
import kr.voiceparking.reservation.domain.ApplyResult;
import kr.voiceparking.reservation.domain.Outcome;
import kr.voiceparking.reservation.domain.Reservation;
import kr.voiceparking.reservation.notification.SnapshotSink;

/**
 * 예약 상태를 확정하는 유일한 곳이다: 생성(W1), 결제 결과 반영(W2), 자동 취소(W3), 조회(W4).
 * 잠금 획득 순서는 세션 잠금, 예약 잠금, 알림이다. 알림 {@code publish}는 예약 잠금 안에서 불리며 입출력이 없다.
 * 모든 시각은 주입한 시계에서 읽는다 (BR5.1).
 */
@Service
public class ReservationService {

    /** 생성 결과. created가 false이면 같은 멱등 키로 이미 만든 예약이다 (200). */
    public record CreateResult(Reservation reservation, boolean created) {
    }

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int MAX_TEXT_LENGTH = 100;

    private final ReservationStore store;
    private final SearchClient searchClient;
    private final SnapshotSink sink;
    private final DeadlineTimers timers;
    private final Clock clock;
    private final Duration paymentTimeout;

    public ReservationService(ReservationStore store, SearchClient searchClient, SnapshotSink sink,
            DeadlineTimers timers, Clock clock, ReservationProperties properties) {
        this.store = store;
        this.searchClient = searchClient;
        this.sink = sink;
        this.timers = timers;
        this.clock = clock;
        this.paymentTimeout = properties.paymentTimeout();
    }

    // ---- W1: 예약 생성 ----

    public CreateResult create(CreateCommand command) {
        validate(command);
        String sessionId = command.sessionId();
        String key = command.idempotencyKey();
        int hours = command.durationHours();

        // 불필요한 u1 호출을 줄이려고 잠금 없이 먼저 한 번 확인한다. 같은 키가 먼저다 (BR1.2, BR1.3).
        Optional<Reservation> existing = store.findByKey(sessionId, key);
        if (existing.isPresent()) {
            return new CreateResult(existing.get(), false);
        }
        rejectIfPending(sessionId);

        // u1 호출은 세션 잠금 밖에서 한다. 최대 1.5초 걸리는 일이 같은 세션의 다른 요청을 막지 않게 한다.
        PassInfo pass = searchClient.lookup(command.passId().trim(), hours);

        return store.withSessionLock(sessionId, () -> {
            Optional<Reservation> again = store.findByKey(sessionId, key);
            if (again.isPresent()) {
                return new CreateResult(again.get(), false);
            }
            rejectIfPending(sessionId);
            Reservation created = Reservation.create(UUID.randomUUID().toString(), sessionId, key,
                    command.passId().trim(), pass.lotName(), hours, pass.price(), now());
            return store.withReservationLock(created.reservationId(), () -> {
                store.save(created);
                timers.schedule(created.reservationId(), created.deadline(paymentTimeout),
                        () -> cancelOnDeadline(created.reservationId()));
                logStateChange(null, created, "CREATED");
                publish(created, System.nanoTime());
                return new CreateResult(created, true);
            });
        });
    }

    /** 세션에 결제 대기 예약이 있으면 기존 예약 ID와 함께 PENDING_EXISTS로 거절한다 (BR1.3). */
    private void rejectIfPending(String sessionId) {
        store.findPending(sessionId).ifPresent(p -> {
            throw new ReservationException(ErrorCode.PENDING_EXISTS, p.reservationId());
        });
    }

    private static void validate(CreateCommand c) {
        boolean valid = c != null
                && c.sessionId() != null && UUID_FORMAT.matcher(c.sessionId()).matches()
                && isText(c.passId())
                && isText(c.idempotencyKey())
                && c.durationHours() != null && c.durationHours() >= 1 && c.durationHours() <= 24;
        if (!valid) {
            throw new ReservationException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static boolean isText(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_TEXT_LENGTH;
    }

    // ---- W2: 결제 결과 반영 ----

    /** 결제 결과를 원자적으로 반영하고 판정을 돌려준다. 같은 eventId에는 처음 낸 판정을 그대로 돌려준다 (BR2.1). */
    public AppliedEvent applyPaymentResult(String reservationId, String eventId, Outcome outcome) {
        long receivedNanos = System.nanoTime();
        Instant receivedAt = now();
        if (!isText(eventId) || outcome == null) {
            throw new ReservationException(ErrorCode.INVALID_REQUEST);
        }
        if (store.findById(reservationId).isEmpty()) {
            throw new ReservationException(ErrorCode.NOT_FOUND);
        }
        return store.withReservationLock(reservationId,
                () -> applyLocked(reservationId, eventId, outcome, receivedAt, receivedNanos));
    }

    private AppliedEvent applyLocked(String reservationId, String eventId, Outcome outcome, Instant receivedAt,
            long receivedNanos) {
        Reservation current = store.findById(reservationId)
                .orElseThrow(() -> new ReservationException(ErrorCode.NOT_FOUND));

        Optional<AppliedEvent> prior = store.findEvent(reservationId, eventId);
        if (prior.isPresent()) {
            logResult("DUPLICATE", current, eventId, outcome, prior.get().reservationStatus().name());
            return prior.get();
        }

        Reservation updated = current;
        ApplyResult result = ApplyResult.REJECTED;
        if (current.isPending()) {
            Instant deadline = current.deadline(paymentTimeout);
            if (!receivedAt.isBefore(deadline)) {
                // 마감 시각과 같거나 지났으면 취소가 이긴다. 타이머가 늦어도 먼저 취소로 확정한다 (BR2.3, D4).
                updated = current.cancel(deadline);
                persist(current, updated, "LATE_RESULT", receivedNanos);
            } else if (outcome == Outcome.APPROVED) {
                updated = current.confirm(receivedAt);
                persist(current, updated, "APPROVED", receivedNanos);
                result = ApplyResult.APPLIED;
            } else {
                updated = current.paymentFailed(receivedAt);
                persist(current, updated, "FAILED", receivedNanos);
                result = ApplyResult.APPLIED;
            }
        }

        AppliedEvent event = new AppliedEvent(eventId, outcome, result, updated.status(), receivedAt);
        store.recordEvent(reservationId, event);
        if (result == ApplyResult.REJECTED) {
            logResult("REJECTED", updated, eventId, outcome, updated.status().name());
        }
        return event;
    }

    // ---- W3: 자동 취소 ----

    /** 마감 타이머가 부른다. 결제 대기일 때만 취소하고 이미 종료된 예약에는 아무것도 하지 않는다 (BR2.4). */
    public void cancelOnDeadline(String reservationId) {
        try {
            store.withReservationLock(reservationId, () -> {
                store.findById(reservationId).filter(Reservation::isPending).ifPresent(current -> {
                    persist(current, current.cancel(now()), "AUTO_CANCEL", System.nanoTime());
                });
                return null;
            });
        } catch (RuntimeException e) {
            log.error("event=auto_cancel_failed reservation={} cause={}", reservationId, e.getClass().getSimpleName(),
                    e);
        } finally {
            timers.cancel(reservationId);
        }
    }

    // ---- W4: 조회 ----

    /** 세션이 소유한 예약. 없거나 다른 세션의 것이면 같은 NOT_FOUND다 (BR3.2). */
    public Reservation get(String sessionId, String reservationId) {
        return store.findById(reservationId)
                .filter(r -> r.sessionId().equals(sessionId))
                .orElseThrow(() -> new ReservationException(ErrorCode.NOT_FOUND));
    }

    /** 세션에서 가장 최근에 만든 예약 (BR3.3). */
    public Reservation current(String sessionId) {
        return store.findLatest(sessionId).orElseThrow(() -> new ReservationException(ErrorCode.NO_RESERVATION));
    }

    // ---- 내부 ----

    /** 값 교체로 저장하고, 결제 대기를 벗어났으면 타이머를 해제하고, 스냅샷을 푸시한다. 예약 잠금 안에서만 부른다. */
    private void persist(Reservation before, Reservation after, String cause, long receivedNanos) {
        store.save(after);
        if (!after.isPending()) {
            timers.cancel(after.reservationId());
        }
        logStateChange(before, after, cause);
        publish(after, receivedNanos);
    }

    /** 푸시 실패는 이미 저장한 상태를 되돌리지 않는다 (BR4.4). */
    private void publish(Reservation reservation, long receivedNanos) {
        try {
            sink.publish(reservation.toSnapshot(), receivedNanos);
        } catch (RuntimeException e) {
            log.error("event=publish_failed reservation={} version={} cause={}", reservation.reservationId(),
                    reservation.version(), e.getClass().getSimpleName(), e);
        }
    }

    private void logStateChange(Reservation before, Reservation after, String cause) {
        log.info("event=state_change reservation={} from={} to={} version={} cause={}", after.reservationId(),
                before == null ? "NONE" : before.status(), after.status(), after.version(), cause);
    }

    private void logResult(String cause, Reservation reservation, String eventId, Outcome outcome, String status) {
        log.info("event=payment_result cause={} reservation={} eventId={} outcome={} status={}", cause,
                reservation.reservationId(), sanitize(eventId), outcome, status);
    }

    /** 로그 위조를 막으려고 제어 문자를 지운다. */
    private static String sanitize(String value) {
        return value.replaceAll("[\\p{Cntrl}]", "_");
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}

package kr.voiceparking.payment.payment;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.voiceparking.payment.payment.PaymentStore.Lease;
import kr.voiceparking.payment.payment.PaymentStore.LockScope;

/**
 * 결제 요청(C2)과 웹훅(C6) 처리, 멱등과 직렬화.
 *
 * <p>결제 요청: (세션, 멱등 키) 잠금 안에서 u2 예약 생성 → Payment 조회 또는 생성 → 모의 PG 등록(PgRequest가 없을 때만).
 * 마감 1.5초는 요청을 받은 때부터 재며 잠금 대기와 u2 호출이 나눠 쓴다 (E2).
 *
 * <p>웹훅: 이벤트 잠금 → 중복 조회 → Payment 잠금 → u2 반영 호출 → 이벤트·상태 기록 (E1). Payment 잠금은 u2 호출 앞에서
 * 얻어 호출 내내 쥐므로 같은 Payment의 웹훅은 u2의 판정 순서대로 반영된다. 잠금 대기는 각각 {@link #DEFAULT_LOCK_WAIT}까지다 (E3).
 * 잠금 획득 순서는 KEY 또는 EVENT, PAYMENT, 저장소 맵이며 역순은 없다.
 */
public class PaymentService {

    /** 웹훅의 잠금 대기 상한 (E3). */
    public static final Duration DEFAULT_LOCK_WAIT = Duration.ofSeconds(7);
    /** 결제 요청의 전체 마감: 요청 수신부터 502까지 (NFR1.5). */
    static final Duration CREATE_DEADLINE = Duration.ofMillis(1500);
    /** 잠금을 얻은 뒤 남은 시간이 이보다 짧으면 u2를 부르지 않고 502로 끝낸다. */
    static final Duration MIN_CALL_TIME = Duration.ofMillis(200);

    private static final int MAX_ID_LENGTH = 100;

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentStore store;
    private final ReservationClient reservations;
    private final PaymentGateway gateway;
    private final Clock clock;
    private final Duration lockWait;

    public PaymentService(PaymentStore store, ReservationClient reservations, PaymentGateway gateway, Clock clock,
            Duration lockWait) {
        this.store = store;
        this.reservations = reservations;
        this.gateway = gateway;
        this.clock = clock;
        this.lockWait = lockWait;
    }

    // ---------------------------------------------------------------- 결제 요청

    /** 결제 요청 한 건을 처리한다. receivedNanos는 요청을 받은 {@link System#nanoTime()} 값이다. */
    public PaymentReceipt request(PaymentCommand command, long receivedNanos) {
        Request request = validate(command);
        long deadline = receivedNanos + CREATE_DEADLINE.toNanos();
        String lockId = PaymentStore.keyLockId(request.sessionId(), request.idempotencyKey());

        Optional<Lease> lease = store.tryLock(LockScope.KEY, lockId, Duration.ofNanos(deadline - System.nanoTime()));
        if (lease.isEmpty()) {
            throw requestFailed(request, ErrorCode.UPSTREAM_FAILED, null, "key_lock_wait");
        }
        try (Lease held = lease.get()) {
            long remaining = deadline - System.nanoTime();
            if (remaining < MIN_CALL_TIME.toNanos()) {
                throw requestFailed(request, ErrorCode.UPSTREAM_FAILED, null, "no_time_left");
            }
            ReservationReply reply = createReservation(request, Duration.ofNanos(remaining));
            return settleRequest(request, reply);
        }
    }

    private PaymentReceipt settleRequest(Request request, ReservationReply reply) {
        Optional<Payment> existing = store.findByKey(request.sessionId(), request.idempotencyKey());
        Payment payment = existing.orElseGet(() -> {
            Payment created = Payment.requested(reply.reservationId(), request.sessionId(),
                    request.idempotencyKey(), request.passId(), request.durationHours(), reply.amount(),
                    request.scenario(), request.delaySeconds(), reply.paymentRequestedAt());
            store.save(created);
            return created;
        });
        boolean register = !store.hasPgRequest(payment.paymentId())
                && (reply.created() || PaymentReceipt.PENDING_PAYMENT.equals(reply.status()));
        if (register) {
            gateway.register(new PgPayment(payment.paymentId(), payment.mockScenario(), payment.mockDelaySeconds()));
            store.markPgRequested(payment.paymentId());
        }
        logRequest(payment.paymentId(), reply.reservationId(), reply.created() && existing.isEmpty() ? "CREATED"
                : "REPLAYED", payment.mockScenario().name());
        return new PaymentReceipt(payment.paymentId(), reply.reservationId(), reply.status(),
                reply.paymentRequestedAt());
    }

    /** u2 오류 가운데 404·409·422는 같은 코드로 전하고, 그 밖의 모든 실패는 UPSTREAM_FAILED로 바꾼다 (BR1.4). */
    private ReservationReply createReservation(Request request, Duration timeout) {
        CreateCall call = new CreateCall(request.sessionId(), request.passId(), request.durationHours(),
                request.idempotencyKey());
        try {
            ReservationReply reply = reservations.create(call, timeout);
            if (reply == null) {
                throw new PaymentException(ErrorCode.UPSTREAM_FAILED);
            }
            return reply;
        } catch (PaymentException e) {
            ErrorCode code = relayable(e.errorCode()) ? e.errorCode() : ErrorCode.UPSTREAM_FAILED;
            throw requestFailed(request, code, relayable(e.errorCode()) ? e.reservationId() : null, "u2_error");
        } catch (RuntimeException e) {
            throw requestFailed(request, ErrorCode.UPSTREAM_FAILED, null, e.getClass().getSimpleName());
        }
    }

    private static boolean relayable(ErrorCode code) {
        return code == ErrorCode.PASS_NOT_FOUND || code == ErrorCode.PENDING_EXISTS
                || code == ErrorCode.DURATION_NOT_AVAILABLE;
    }

    private PaymentException requestFailed(Request request, ErrorCode code, String reservationId, String why) {
        log.info("event=payment_request payment=- reservation={} result={} scenario={}", reservationId == null ? "-"
                : safe(reservationId), code.name(), request.scenario().name());
        if (code == ErrorCode.UPSTREAM_FAILED) {
            log.warn("event=payment_request_cause cause=UPSTREAM_FAILED detail={}", safe(why));
        }
        return new PaymentException(code, reservationId);
    }

    private void logRequest(String paymentId, String reservationId, String result, String scenario) {
        log.info("event=payment_request payment={} reservation={} result={} scenario={}", safe(paymentId),
                safe(reservationId), result, scenario);
    }

    private record Request(String sessionId, String passId, int durationHours, String idempotencyKey,
            MockScenario scenario, int delaySeconds) {
    }

    /** BR1.1: 세션은 UUID, passId·idempotencyKey는 1~100자, durationHours 1~24, 시나리오와 지연은 허용값. */
    private static Request validate(PaymentCommand c) {
        if (c.sessionId() == null || !PaymentCommand.UUID_FORMAT.matcher(c.sessionId()).matches()) {
            throw new PaymentException(ErrorCode.MISSING_SESSION);
        }
        if (isBadId(c.passId()) || isBadId(c.idempotencyKey())) {
            throw new PaymentException(ErrorCode.INVALID_REQUEST);
        }
        if (c.durationHours() == null || c.durationHours() < 1 || c.durationHours() > 24) {
            throw new PaymentException(ErrorCode.INVALID_REQUEST);
        }
        MockScenario scenario = MockScenario.APPROVE;
        if (c.mockScenario() != null) {
            try {
                scenario = MockScenario.valueOf(c.mockScenario());
            } catch (IllegalArgumentException e) {
                throw new PaymentException(ErrorCode.INVALID_REQUEST);
            }
        }
        int delay = c.mockDelaySeconds() == null ? 0 : c.mockDelaySeconds();
        if (delay < 0 || delay > 600) {
            throw new PaymentException(ErrorCode.INVALID_REQUEST);
        }
        return new Request(c.sessionId(), c.passId(), c.durationHours(), c.idempotencyKey(), scenario, delay);
    }

    private static boolean isBadId(String value) {
        return value == null || value.isBlank() || value.length() > MAX_ID_LENGTH;
    }

    // ---------------------------------------------------------------- 웹훅

    /** 서명과 형식 검증을 마친 웹훅 한 건을 처리한다. */
    public Handled handleWebhook(WebhookCall call) {
        long start = System.nanoTime();
        Payment known = store.findById(call.paymentId())
                .orElseThrow(() -> new PaymentException(ErrorCode.UNKNOWN_PAYMENT));
        String result = "ERROR";
        try {
            Handled handled = process(call, known);
            result = handled.name();
            return handled;
        } catch (PaymentException e) {
            result = e.errorCode().name();
            throw e;
        } finally {
            log.info("event=webhook payment={} reservation={} event_id={} outcome={} result={} ms={}",
                    safe(known.paymentId()), safe(known.reservationId()), safe(call.eventId()), call.outcome(),
                    result, (System.nanoTime() - start) / 1_000_000);
        }
    }

    private Handled process(WebhookCall call, Payment known) {
        try (Lease eventLock = lock(LockScope.EVENT, call.eventId(), call, known)) {
            if (store.findEvent(call.eventId()).isPresent()) {
                return Handled.DUPLICATE;
            }
            try (Lease paymentLock = lock(LockScope.PAYMENT, known.paymentId(), call, known)) {
                Payment current = store.findById(known.paymentId()).orElse(known);
                ApplyReply reply = apply(call, current);
                return record(call, current, reply);
            }
        }
    }

    private Handled record(WebhookCall call, Payment current, ApplyReply reply) {
        if (reply.result() == ApplyResult.APPLIED) {
            store.recordEvent(new WebhookEvent(call.eventId(), current.paymentId(), call.outcome(), clock.instant(),
                    Handled.APPLIED));
            settle(current, PaymentStatus.of(call.outcome()));
            return Handled.APPLIED;
        }
        store.recordEvent(new WebhookEvent(call.eventId(), current.paymentId(), call.outcome(), clock.instant(),
                Handled.IGNORED));
        settle(current, PaymentStatus.IGNORED);
        log.warn("event=webhook_cause cause=IGNORED payment={} reservation={} event_id={} outcome={} "
                + "reservation_status={}", safe(current.paymentId()), safe(current.reservationId()),
                safe(call.eventId()), call.outcome(), safe(reply.reservationStatus()));
        return Handled.IGNORED;
    }

    /** 결제 상태는 아직 REQUESTED일 때만 바꾼다. 종료된 Payment는 그대로 두고 이벤트만 기록한다 (E8). */
    private void settle(Payment current, PaymentStatus target) {
        if (current.status() == PaymentStatus.REQUESTED) {
            store.save(current.settle(target));
        }
    }

    private ApplyReply apply(WebhookCall call, Payment current) {
        try {
            ApplyReply reply = reservations.applyPaymentResult(current.reservationId(), call.eventId(),
                    call.outcome());
            if (reply == null || reply.result() == null) {
                throw new PaymentException(ErrorCode.UPSTREAM_FAILED);
            }
            return reply;
        } catch (RuntimeException e) {
            throw upstreamFailed(call, current, e.getClass().getSimpleName());
        }
    }

    private Lease lock(LockScope scope, String id, WebhookCall call, Payment payment) {
        return store.tryLock(scope, id, lockWait)
                .orElseThrow(() -> upstreamFailed(call, payment, scope.name() + "_lock_wait"));
    }

    private PaymentException upstreamFailed(WebhookCall call, Payment payment, String why) {
        log.warn("event=webhook_cause cause=UPSTREAM_FAILED payment={} reservation={} event_id={} detail={}",
                safe(payment.paymentId()), safe(payment.reservationId()), safe(call.eventId()), safe(why));
        return new PaymentException(ErrorCode.UPSTREAM_FAILED);
    }

    /** 로그 줄을 위조할 수 없도록 허용 문자 밖의 값(제어 문자, 공백, '=')을 밑줄로 바꾼다. */
    private static String safe(String value) {
        return value == null ? "-" : value.replaceAll("[^\\w.:@/-]", "_");
    }
}

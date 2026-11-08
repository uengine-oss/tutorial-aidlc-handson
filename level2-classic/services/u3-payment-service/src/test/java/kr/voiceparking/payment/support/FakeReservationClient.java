package kr.voiceparking.payment.support;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import kr.voiceparking.payment.payment.ApplyReply;
import kr.voiceparking.payment.payment.ApplyResult;
import kr.voiceparking.payment.payment.CreateCall;
import kr.voiceparking.payment.payment.Outcome;
import kr.voiceparking.payment.payment.PaymentException;
import kr.voiceparking.payment.payment.ReservationClient;
import kr.voiceparking.payment.payment.ReservationReply;

/**
 * u2를 흉내 내는 가짜. 예약 생성은 (세션, 키)로 멱등이다: 처음은 created=true, 그다음은 created=false와
 * {@link #currentStatus}를 돌려준다. 결과 반영은 {@link #script}에 넣은 응답(또는 예외)을 차례로 돌려주고, 비어 있으면
 * APPLIED/CONFIRMED를 돌려준다.
 */
public final class FakeReservationClient implements ReservationClient {

    public record Apply(String reservationId, String eventId, Outcome outcome) {
    }

    public static final Instant REQUESTED_AT = Instant.parse("2026-11-05T01:00:00Z");

    public final AtomicInteger createCalls = new AtomicInteger();
    public final AtomicInteger applyCalls = new AtomicInteger();
    public final AtomicInteger maxApplyInFlight = new AtomicInteger();
    private final AtomicInteger applyInFlight = new AtomicInteger();

    private final List<CreateCall> creates = Collections.synchronizedList(new ArrayList<>());
    private final List<Apply> applies = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, ReservationReply> byKey = new HashMap<>();
    private final Deque<Object> applyScript = new ConcurrentLinkedDeque<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public volatile RuntimeException createFailure;
    /** true이면 받은 제한 시간만큼 기다린 뒤 UPSTREAM_FAILED로 실패한다(응답 없는 u2). */
    public volatile boolean hangOnCreate;
    public volatile Duration createDelay = Duration.ZERO;
    public volatile Duration applyDelay = Duration.ZERO;
    public volatile String currentStatus = "PENDING_PAYMENT";
    public volatile int amount = 4000;
    public volatile Duration lastCreateTimeout;

    /** 스프링 컨텍스트를 공유하는 시험이 서로 영향을 주지 않도록 처음 상태로 되돌린다. */
    public void reset() {
        createCalls.set(0);
        applyCalls.set(0);
        maxApplyInFlight.set(0);
        creates.clear();
        applies.clear();
        synchronized (byKey) {
            byKey.clear();
        }
        applyScript.clear();
        sequence.set(0);
        createFailure = null;
        hangOnCreate = false;
        createDelay = Duration.ZERO;
        applyDelay = Duration.ZERO;
        currentStatus = "PENDING_PAYMENT";
        amount = 4000;
        lastCreateTimeout = null;
    }

    /** 결과 반영 응답 하나(또는 던질 예외 하나)를 줄 세운다. */
    public void script(Object reply) {
        applyScript.add(reply);
    }

    public List<CreateCall> creates() {
        synchronized (creates) {
            return new ArrayList<>(creates);
        }
    }

    public List<Apply> applies() {
        synchronized (applies) {
            return new ArrayList<>(applies);
        }
    }

    @Override
    public ReservationReply create(CreateCall call, Duration timeout) {
        createCalls.incrementAndGet();
        creates.add(call);
        lastCreateTimeout = timeout;
        if (hangOnCreate) {
            sleep(timeout);
            throw new PaymentException(kr.voiceparking.payment.payment.ErrorCode.UPSTREAM_FAILED);
        }
        sleep(createDelay);
        RuntimeException failure = createFailure;
        if (failure != null) {
            throw failure;
        }
        synchronized (byKey) {
            String key = call.sessionId() + "|" + call.idempotencyKey();
            ReservationReply existing = byKey.get(key);
            if (existing != null) {
                return new ReservationReply(false, existing.reservationId(), currentStatus, existing.amount(),
                        existing.durationHours(), existing.lotName(), existing.paymentRequestedAt());
            }
            ReservationReply created = new ReservationReply(true, "res-" + sequence.incrementAndGet(),
                    "PENDING_PAYMENT", amount, call.durationHours(), "강남 공영주차장", REQUESTED_AT);
            byKey.put(key, created);
            return created;
        }
    }

    @Override
    public ApplyReply applyPaymentResult(String reservationId, String eventId, Outcome outcome) {
        applyCalls.incrementAndGet();
        applies.add(new Apply(reservationId, eventId, outcome));
        int inFlight = applyInFlight.incrementAndGet();
        maxApplyInFlight.accumulateAndGet(inFlight, Math::max);
        try {
            sleep(applyDelay);
            Object next = applyScript.poll();
            if (next instanceof RuntimeException failure) {
                throw failure;
            }
            if (next instanceof ApplyReply reply) {
                return reply;
            }
            return new ApplyReply(ApplyResult.APPLIED, outcome == Outcome.APPROVED ? "CONFIRMED" : "PAYMENT_FAILED");
        } finally {
            applyInFlight.decrementAndGet();
        }
    }

    private static void sleep(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

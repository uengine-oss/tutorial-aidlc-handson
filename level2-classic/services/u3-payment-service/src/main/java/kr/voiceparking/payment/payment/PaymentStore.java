package kr.voiceparking.payment.payment;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * 인메모리 저장소: Payment, WebhookEvent, 모의 PG 요청 기록, 그리고 키별 잠금 (BR5.1). 재시작하면 모두 사라진다.
 * 잠금은 (범위, 식별자)마다 따로라 서로 다른 키·이벤트·Payment는 서로 막지 않고, 같은 것은 도착 순서대로 하나씩
 * 지난다 (NFR4.2). 잠금 획득 순서는 KEY 또는 EVENT, 그다음 PAYMENT, 마지막이 맵(잎)이며 역순은 없다.
 */
@Component
public class PaymentStore {

    /** 잠금의 범위. 같은 식별자 문자열이라도 범위가 다르면 서로 다른 잠금이다. */
    public enum LockScope {
        /** (세션, 멱등 키) 단위: 결제 요청 직렬화 */
        KEY,
        /** eventId 단위: 웹훅 중복 직렬화 */
        EVENT,
        /** paymentId 단위: 같은 Payment의 웹훅 직렬화 */
        PAYMENT
    }

    /** 얻은 잠금. 닫으면 풀리고, 두 번 닫아도 한 번만 푼다. */
    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }

    private final Map<String, Payment> paymentsById = new ConcurrentHashMap<>();
    private final Map<String, String> paymentIdByKey = new ConcurrentHashMap<>();
    private final Map<String, WebhookEvent> events = new ConcurrentHashMap<>();
    private final Set<String> pgRequested = ConcurrentHashMap.newKeySet();
    private final Map<String, Semaphore> locks = new ConcurrentHashMap<>();

    /** Payment를 저장하거나(처음) 같은 paymentId의 값을 바꾼다. 같은 (세션, 키)에 다른 Payment가 있으면 거부한다. */
    public void save(Payment payment) {
        String key = keyLockId(payment.sessionId(), payment.idempotencyKey());
        String owner = paymentIdByKey.putIfAbsent(key, payment.paymentId());
        if (owner != null && !owner.equals(payment.paymentId())) {
            throw new IllegalStateException("idempotency key already used by another payment");
        }
        paymentsById.put(payment.paymentId(), payment);
    }

    public Optional<Payment> findById(String paymentId) {
        return Optional.ofNullable(paymentsById.get(paymentId));
    }

    public Optional<Payment> findByKey(String sessionId, String idempotencyKey) {
        String paymentId = paymentIdByKey.get(keyLockId(sessionId, idempotencyKey));
        return paymentId == null ? Optional.empty() : findById(paymentId);
    }

    /** 처음 기록하면 true. 이미 있는 eventId는 처음 기록을 그대로 두고 false를 돌려준다. */
    public boolean recordEvent(WebhookEvent event) {
        return events.putIfAbsent(event.eventId(), event) == null;
    }

    public Optional<WebhookEvent> findEvent(String eventId) {
        return Optional.ofNullable(events.get(eventId));
    }

    /** 이 결제를 PG에 등록했다고 기록한다 (PgRequest). */
    public void markPgRequested(String paymentId) {
        pgRequested.add(paymentId);
    }

    public boolean hasPgRequest(String paymentId) {
        return pgRequested.contains(paymentId);
    }

    /**
     * 최대 wait만큼 기다려 잠금을 얻는다. 못 얻거나 기다리다 중단되면 비어 있는 값을 돌려준다. 같은 호출자가 이미 쥔
     * 잠금을 다시 얻으려 해도 얻지 못한다(재진입 없음).
     */
    public Optional<Lease> tryLock(LockScope scope, String id, Duration wait) {
        Semaphore semaphore = locks.computeIfAbsent(scope + ":" + id, k -> new Semaphore(1, true));
        try {
            if (!semaphore.tryAcquire(Math.max(0, wait.toNanos()), TimeUnit.NANOSECONDS)) {
                return Optional.empty();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        AtomicBoolean released = new AtomicBoolean();
        return Optional.of(() -> {
            if (released.compareAndSet(false, true)) {
                semaphore.release();
            }
        });
    }

    /** (세션, 멱등 키)를 모호하지 않게 하나의 식별자로 합친다. */
    public static String keyLockId(String sessionId, String idempotencyKey) {
        return sessionId.length() + ":" + sessionId + ":" + idempotencyKey;
    }
}

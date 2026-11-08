package kr.voiceparking.reservation.notification;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.voiceparking.reservation.config.ReservationProperties.Push;

/**
 * 예약마다 최신 스냅샷(version이 높은 것만)과 구독 목록을 가진다. 이 패키지는 reservation과 domain을
 * 참조하지 않는다 (NFR4.5).
 * {@link #publish}는 입출력 없이 최신 스냅샷을 갱신하고 각 연결의 큐에 넣기만 한다. 쓰기는 연결별 배출
 * 작업이 푸시 실행기에서 한다. 구독 등록과 발행은 예약별 모니터 안에서 같이 일어나므로 첫 이벤트는 등록
 * 시점의 최신본이고 이후 이벤트가 빠짐없이 뒤따른다.
 */
public class NotificationService implements SnapshotSink, Connection.Owner, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Duration SLOW_WRITE_CHECK_INTERVAL = Duration.ofMillis(100);

    /** 예약 하나의 상태. 최신 스냅샷과 구독 목록을 같은 모니터로 지킨다. */
    private static final class State {
        Snapshot latest;
        final CopyOnWriteArrayList<Connection> connections = new CopyOnWriteArrayList<>();
    }

    private final Push settings;
    private final Executor pushExecutor;
    private final ScheduledExecutorService watchdog;
    private final Runnable onClose;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final AtomicInteger subscriptions = new AtomicInteger();

    public NotificationService(Push settings, Executor pushExecutor, ScheduledExecutorService watchdog) {
        this(settings, pushExecutor, watchdog, () -> { });
    }

    public NotificationService(Push settings, Executor pushExecutor, ScheduledExecutorService watchdog,
            Runnable onClose) {
        this.settings = settings;
        this.pushExecutor = pushExecutor;
        this.watchdog = watchdog;
        this.onClose = onClose;
    }

    /** 감시 스케줄러에 연결 유지 신호와 느린 쓰기 감시를 올린다. */
    public void start() {
        watchdog.scheduleWithFixedDelay(this::sendKeepAlives, settings.keepAliveInterval().toMillis(),
                settings.keepAliveInterval().toMillis(), TimeUnit.MILLISECONDS);
        watchdog.scheduleWithFixedDelay(this::checkSlowWrites, SLOW_WRITE_CHECK_INTERVAL.toMillis(),
                SLOW_WRITE_CHECK_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        onClose.run();
    }

    @Override
    public void publish(Snapshot snapshot, long receivedNanos) {
        State state = states.computeIfAbsent(snapshot.reservationId(), id -> new State());
        synchronized (state) {
            if (state.latest != null && snapshot.version() <= state.latest.version()) {
                return;
            }
            state.latest = snapshot;
            int connectionCount = state.connections.size();
            for (Connection connection : state.connections) {
                connection.enqueue(new Connection.Item(snapshot, receivedNanos, connectionCount));
            }
        }
    }

    /**
     * 구독을 등록하고 그 시점의 최신 스냅샷을 연결 큐의 첫 항목으로 넣는다. 보관 중인 것이 없거나 대체
     * 스냅샷보다 낮으면 대체 스냅샷(웹 계층이 Reservation의 현재 상태로 만든 것)을 첫 항목으로 쓴다.
     *
     * @throws SubscriptionLimitException 동시 구독 수 상한을 넘었을 때
     */
    public Subscription subscribe(String reservationId, EventChannel channel, Snapshot fallback) {
        reserveSlot();
        Connection connection = new Connection(reservationId, channel, pushExecutor, settings.queueLimit(), this);
        State state = states.computeIfAbsent(reservationId, id -> new State());
        synchronized (state) {
            state.connections.add(connection);
            Snapshot first = state.latest != null && state.latest.version() >= fallback.version()
                    ? state.latest : fallback;
            connection.enqueue(new Connection.Item(first, 0, state.connections.size()));
        }
        log.info("event=subscribe_open reservation={} connections={}", reservationId, subscriptions.get());
        return connection;
    }

    public Optional<Snapshot> latest(String reservationId) {
        State state = states.get(reservationId);
        if (state == null) {
            return Optional.empty();
        }
        synchronized (state) {
            return Optional.ofNullable(state.latest);
        }
    }

    public int subscriptionCount() {
        return subscriptions.get();
    }

    /** 15초마다 감시 스케줄러가 부른다. 신호도 연결 큐를 거치므로 이벤트 쓰기와 겹치지 않는다 (BR4.5). */
    public void sendKeepAlives() {
        forEachConnection(connection -> connection.enqueue(Connection.Item.keepAlive()));
    }

    /** 쓰기 하나가 제한을 넘기면 그 연결을 닫는다. 막힌 스레드가 돌아오기를 기다리지 않는다 (NFR5.4, D5). */
    public void checkSlowWrites() {
        long limitNanos = settings.writeTimeout().toNanos();
        forEachConnection(connection -> {
            long started = connection.writeStartedNanos();
            if (started != 0 && System.nanoTime() - started > limitNanos) {
                connection.terminate("WRITE_TIMEOUT");
            }
        });
    }

    private void forEachConnection(Consumer<Connection> action) {
        for (State state : states.values()) {
            for (Connection connection : state.connections) {
                action.accept(connection);
            }
        }
    }

    private void reserveSlot() {
        while (true) {
            int current = subscriptions.get();
            if (current >= settings.maxSubscriptions()) {
                throw new SubscriptionLimitException();
            }
            if (subscriptions.compareAndSet(current, current + 1)) {
                return;
            }
        }
    }

    // ---- Connection.Owner ----

    @Override
    public void removed(Connection connection, String cause) {
        State state = states.get(connection.reservationId());
        if (state != null) {
            state.connections.remove(connection);
        }
        // 구독을 먼저 제거한 뒤 상한을 센다 (D7).
        subscriptions.decrementAndGet();
        log.info("event=subscribe_close reservation={} cause={}", connection.reservationId(), cause);
    }

    @Override
    public void closeChannelLater(Connection connection, String cause) {
        // 쓰기가 막힌 연결의 닫기가 호출 스레드를 붙잡을 수 있으므로 전용 스레드에서 닫는다 (D5).
        Thread closer = new Thread(() -> {
            try {
                connection.channel().close(cause);
            } catch (RuntimeException e) {
                log.warn("event=close_failed reservation={} cause={}", connection.reservationId(),
                        e.getClass().getSimpleName());
            }
        }, "push-closer");
        closer.setDaemon(true);
        closer.start();
    }
}

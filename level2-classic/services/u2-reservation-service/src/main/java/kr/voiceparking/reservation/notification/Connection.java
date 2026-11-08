package kr.voiceparking.reservation.notification;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 구독 연결 하나. 연결별 FIFO 큐(상한 있음)와 "배출 중" 표시를 가지고, 큐가 비어 있지 않으면 푸시 실행기에
 * 배출 작업을 하나만 낸다. 그래서 한 연결의 쓰기는 겹치지 않고 순서가 뒤집히지 않는다.
 * {@link #enqueue}는 입출력 없이 큐에 넣기만 하므로 예약 단위 잠금 안에서 불러도 된다.
 */
final class Connection implements Subscription {

    /** 큐 항목. snapshot이 null이면 연결 유지 신호다. */
    record Item(Snapshot snapshot, long receivedNanos, int connections) {
        static Item keepAlive() {
            return new Item(null, 0, 0);
        }
    }

    /** 연결이 제거될 때 알림 서비스에 알린다. */
    interface Owner {
        void removed(Connection connection, String cause);

        void closeChannelLater(Connection connection, String cause);
    }

    private static final Logger log = LoggerFactory.getLogger(Connection.class);

    private final String reservationId;
    private final EventChannel channel;
    private final Executor executor;
    private final int queueLimit;
    private final Owner owner;

    private final Deque<Item> queue = new ArrayDeque<>();
    private boolean draining;
    private boolean closed;
    private int lastVersion;
    private volatile long writeStartedNanos;

    Connection(String reservationId, EventChannel channel, Executor executor, int queueLimit, Owner owner) {
        this.reservationId = reservationId;
        this.channel = channel;
        this.executor = executor;
        this.queueLimit = queueLimit;
        this.owner = owner;
    }

    String reservationId() {
        return reservationId;
    }

    EventChannel channel() {
        return channel;
    }

    /** 쓰기가 진행 중이면 시작 시각, 아니면 0. 감시 스케줄러가 읽는다. */
    long writeStartedNanos() {
        return writeStartedNanos;
    }

    /**
     * 큐 끝에 넣는다. 이 연결이 이미 보낸(또는 큐에 넣은) version 이하의 스냅샷은 버려서 연결마다 version이
     * 단조 증가하게 한다. 큐가 가득 차면 이 연결만 닫는다.
     */
    void enqueue(Item item) {
        boolean startDrain = false;
        boolean overflow = false;
        synchronized (this) {
            if (closed) {
                return;
            }
            if (item.snapshot() != null) {
                if (item.snapshot().version() <= lastVersion) {
                    return;
                }
            }
            if (queue.size() >= queueLimit) {
                overflow = true;
            } else {
                if (item.snapshot() != null) {
                    lastVersion = item.snapshot().version();
                }
                queue.addLast(item);
                if (!draining) {
                    draining = true;
                    startDrain = true;
                }
            }
        }
        if (overflow) {
            terminate("QUEUE_OVERFLOW");
        } else if (startDrain) {
            submitDrain();
        }
    }

    private void submitDrain() {
        try {
            executor.execute(this::drain);
        } catch (RejectedExecutionException e) {
            synchronized (this) {
                draining = false;
            }
            terminate("EXECUTOR_REJECTED");
        }
    }

    private void drain() {
        while (true) {
            Item item;
            synchronized (this) {
                if (closed || queue.isEmpty()) {
                    draining = false;
                    return;
                }
                item = queue.pollFirst();
            }
            writeStartedNanos = System.nanoTime();
            try {
                if (item.snapshot() == null) {
                    channel.sendKeepAlive();
                } else {
                    channel.sendSnapshot(item.snapshot());
                }
            } catch (Exception e) {
                writeStartedNanos = 0;
                log.warn("event=push_failed reservation={} cause={}", reservationId, e.getClass().getSimpleName());
                terminate("WRITE_FAILED");
                return;
            } finally {
                writeStartedNanos = 0;
            }
            logPush(item);
        }
    }

    private void logPush(Item item) {
        if (item.snapshot() != null && item.receivedNanos() != 0) {
            long ms = (System.nanoTime() - item.receivedNanos()) / 1_000_000;
            log.info("event=push reservation={} version={} connections={} ms={}", reservationId,
                    item.snapshot().version(), item.connections(), ms);
        }
    }

    /** 서버가 이 연결을 끊는다: 구독을 제거하고 채널은 별도 스레드에서 닫는다. */
    void terminate(String cause) {
        if (markClosed()) {
            owner.removed(this, cause);
            owner.closeChannelLater(this, cause);
        }
    }

    @Override
    public void detach(String cause) {
        if (markClosed()) {
            owner.removed(this, cause);
        }
    }

    private synchronized boolean markClosed() {
        if (closed) {
            return false;
        }
        closed = true;
        queue.clear();
        return true;
    }
}

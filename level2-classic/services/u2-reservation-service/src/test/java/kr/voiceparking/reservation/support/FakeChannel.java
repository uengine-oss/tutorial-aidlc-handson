package kr.voiceparking.reservation.support;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import kr.voiceparking.reservation.notification.EventChannel;
import kr.voiceparking.reservation.notification.Snapshot;

/** 쓰기를 막거나 실패하게 만들 수 있는 가짜 연결. 동시에 겹친 쓰기의 최대 개수를 센다. */
public final class FakeChannel implements EventChannel {

    private final List<Snapshot> snapshots = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger keepAlives = new AtomicInteger();
    private final AtomicInteger inWrite = new AtomicInteger();
    private final AtomicInteger maxConcurrentWrites = new AtomicInteger();
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());

    private volatile CountDownLatch gate;
    private volatile boolean failWrites;
    private volatile long writeDelayMs;
    private volatile String closedCause;

    /** 이후의 쓰기는 {@link #release()}가 불릴 때까지 막힌다. */
    public void blockWrites() {
        gate = new CountDownLatch(1);
    }

    public void release() {
        CountDownLatch g = gate;
        if (g != null) {
            g.countDown();
        }
    }

    public void failWrites() {
        failWrites = true;
    }

    public void delayWrites(long millis) {
        writeDelayMs = millis;
    }

    @Override
    public void sendSnapshot(Snapshot snapshot) throws IOException {
        write("snapshot:" + snapshot.version());
        snapshots.add(snapshot);
    }

    @Override
    public void sendKeepAlive() throws IOException {
        write("keep-alive");
        keepAlives.incrementAndGet();
    }

    private void write(String label) throws IOException {
        int now = inWrite.incrementAndGet();
        maxConcurrentWrites.accumulateAndGet(now, Math::max);
        try {
            if (failWrites) {
                throw new IOException("broken pipe");
            }
            CountDownLatch g = gate;
            if (g != null) {
                try {
                    g.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (writeDelayMs > 0) {
                try {
                    Thread.sleep(writeDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            events.add(label);
        } finally {
            inWrite.decrementAndGet();
        }
    }

    @Override
    public void close(String cause) {
        closedCause = cause;
    }

    public List<Snapshot> snapshots() {
        synchronized (snapshots) {
            return new ArrayList<>(snapshots);
        }
    }

    public List<Integer> versions() {
        return snapshots().stream().map(Snapshot::version).toList();
    }

    public List<String> events() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    public int keepAlives() {
        return keepAlives.get();
    }

    public int maxConcurrentWrites() {
        return maxConcurrentWrites.get();
    }

    public boolean isClosed() {
        return closedCause != null;
    }

    public String closedCause() {
        return closedCause;
    }
}

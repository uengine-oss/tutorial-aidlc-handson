package kr.voiceparking.reservation.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kr.voiceparking.reservation.notification.Snapshot;
import kr.voiceparking.reservation.notification.SnapshotSink;

/** Notification 대신 밀어 넣은 스냅샷을 기록하는 가짜 수신자. */
public final class RecordingSink implements SnapshotSink {

    private final List<Snapshot> published = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publish(Snapshot snapshot, long receivedNanos) {
        published.add(snapshot);
    }

    public List<Snapshot> published() {
        synchronized (published) {
            return new ArrayList<>(published);
        }
    }

    public int count() {
        return published.size();
    }

    public Snapshot last() {
        synchronized (published) {
            return published.get(published.size() - 1);
        }
    }
}

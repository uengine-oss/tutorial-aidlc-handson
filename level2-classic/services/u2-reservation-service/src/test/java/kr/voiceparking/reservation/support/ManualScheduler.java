package kr.voiceparking.reservation.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.springframework.scheduling.TaskScheduler;

/** 예약된 작업을 실행하지 않고 기록만 하는 스케줄러. 시험이 콜백을 직접 부른다 (NFR2.4). */
public final class ManualScheduler {

    public record Scheduled(Runnable task, Instant at, ScheduledFuture<?> future) {
    }

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final List<Scheduled> scheduled = Collections.synchronizedList(new ArrayList<>());

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ManualScheduler() {
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            ScheduledFuture future = mock(ScheduledFuture.class);
            scheduled.add(new Scheduled(invocation.getArgument(0), invocation.getArgument(1), future));
            return future;
        });
    }

    public TaskScheduler scheduler() {
        return scheduler;
    }

    public List<Scheduled> scheduled() {
        synchronized (scheduled) {
            return new ArrayList<>(scheduled);
        }
    }

    public Scheduled only() {
        List<Scheduled> all = scheduled();
        if (all.size() != 1) {
            throw new AssertionError("expected exactly one scheduled task but was " + all.size());
        }
        return all.get(0);
    }
}

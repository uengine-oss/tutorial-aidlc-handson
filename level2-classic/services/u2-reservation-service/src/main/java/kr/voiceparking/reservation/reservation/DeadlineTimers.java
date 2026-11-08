package kr.voiceparking.reservation.reservation;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * 예약마다 마감 시각에 한 번 울리는 타이머. 스케줄러는 주입한 시계를 쓴다 (BR5.1).
 * 예약이 결제 대기를 벗어나면 해제한다. 이미 울리기 시작한 타이머는 서비스가 상태를 보고 무시한다 (BR2.4).
 */
@Component
public class DeadlineTimers {

    private final TaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public DeadlineTimers(@Qualifier("deadlineScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void schedule(String reservationId, Instant at, Runnable task) {
        ScheduledFuture<?> future = scheduler.schedule(task, at);
        ScheduledFuture<?> previous = futures.put(reservationId, future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public void cancel(String reservationId) {
        ScheduledFuture<?> future = futures.remove(reservationId);
        if (future != null) {
            future.cancel(false);
        }
    }
}

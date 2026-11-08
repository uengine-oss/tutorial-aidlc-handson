package kr.voiceparking.reservation.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import kr.voiceparking.reservation.notification.NotificationService;

/**
 * 푸시 실행기(스레드 상한은 구독 수 상한의 2배, 큐 없는 직접 인계)와 감시 스케줄러(스레드 1개)를 만든다.
 * 실행기가 작업을 거절하면 그 연결을 닫는다. 구독 수가 스레드 수를 넘지 않고 연결당 배출 작업이 하나뿐이라
 * 느린 연결이 스레드를 모두 잡아 다른 연결을 굶길 수 없다 (D5).
 * 실행기를 Executor 빈으로 노출하지 않는다: 스프링의 기본 작업 실행기를 대체하지 않으려는 것이다.
 */
@Configuration
public class PushConfig {

    @Bean(initMethod = "start", destroyMethod = "close")
    public NotificationService notificationService(ReservationProperties properties) {
        int maxThreads = Math.max(2, properties.push().maxSubscriptions() * 2);
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor pushExecutor = new ThreadPoolExecutor(0, maxThreads, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(), runnable -> daemon(runnable, "push-" + counter.incrementAndGet()));
        ScheduledExecutorService watchdog = Executors
                .newSingleThreadScheduledExecutor(runnable -> daemon(runnable, "push-watchdog"));
        return new NotificationService(properties.push(), pushExecutor, watchdog, () -> {
            pushExecutor.shutdownNow();
            watchdog.shutdownNow();
        });
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }
}

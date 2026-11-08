package kr.voiceparking.payment.config;

import java.time.Clock;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 모든 시각의 유일한 출처인 시계, 모의 PG의 지연 스케줄러(스레드 1개), 그리고 발송 실행기(스레드 4개, 큐 64)를 둔다.
 * 스케줄러는 시각이 되면 발송 실행기에 넘기기만 하므로 느린 웹훅 자기 호출이 다른 타이머를 밀지 않는다 (NFR1.6, NFR4.5).
 */
@Configuration
public class TimeConfig {

    static final int DISPATCH_THREADS = 4;
    static final int DISPATCH_QUEUE = 64;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(name = "pgScheduler")
    public ThreadPoolTaskScheduler pgScheduler(Clock clock) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("pg-timer-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setClock(clock);
        return scheduler;
    }

    @Bean(name = "pgDispatchExecutor", destroyMethod = "shutdownNow")
    public ThreadPoolExecutor pgDispatchExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "pg-dispatch-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(DISPATCH_THREADS, DISPATCH_THREADS, 0L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(DISPATCH_QUEUE), factory);
    }
}

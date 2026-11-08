package kr.voiceparking.reservation.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 모든 시각의 유일한 출처인 시계와, 같은 시계를 쓰는 마감 타이머 스케줄러(스레드 2개) (BR5.1). */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(name = "deadlineScheduler")
    public ThreadPoolTaskScheduler deadlineScheduler(Clock clock) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("deadline-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setClock(clock);
        return scheduler;
    }
}

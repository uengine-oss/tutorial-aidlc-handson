package kr.voiceparking.reservation.reservation;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * u1 주차권 조회(C4)를 RestClient로 호출하고 재시도는 직접 짠 반복으로 한다 (재시도 라이브러리를 쓰지 않는다).
 * 전체 마감 1.5초, 시도 하나의 제한 350ms, 시도 사이 간격 200ms, 남은 시간이 200ms 미만이면 새 시도를 시작하지
 * 않고 최대 3회 호출한다. 읽기 전용이라 재시도해도 안전하다. 404와 422는 재시도하지 않고 그대로 전한다 (BR1.4).
 */
public class HttpSearchClient implements SearchClient {

    static final Duration OVERALL_DEADLINE = Duration.ofMillis(1500);
    static final Duration ATTEMPT_TIMEOUT = Duration.ofMillis(350);
    static final Duration RETRY_GAP = Duration.ofMillis(200);
    static final int MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(HttpSearchClient.class);

    private final String baseUrl;

    public HttpSearchClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public PassInfo lookup(String passId, int durationHours) {
        long deadline = System.nanoTime() + OVERALL_DEADLINE.toNanos();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long remaining = deadline - System.nanoTime();
            if (attempt > 1 && remaining < RETRY_GAP.toNanos()) {
                break;
            }
            Duration timeout = Duration.ofNanos(Math.min(ATTEMPT_TIMEOUT.toNanos(), remaining));
            try {
                return attemptOnce(passId, durationHours, timeout);
            } catch (ReservationException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn("event=upstream_call target=u1 attempt={} outcome=failed cause={}", attempt,
                        e.getClass().getSimpleName());
            }
            if (attempt < MAX_ATTEMPTS && !pause(RETRY_GAP)) {
                break;
            }
        }
        throw new ReservationException(ErrorCode.UPSTREAM_FAILED);
    }

    private PassInfo attemptOnce(String passId, int durationHours, Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        RestClient client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        return client.get()
                .uri("/internal/passes/{passId}?durationHours={hours}", passId, durationHours)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 200) {
                        PassInfo body = response.bodyTo(PassInfo.class);
                        if (body == null) {
                            throw new IllegalStateException("empty body");
                        }
                        return body;
                    }
                    if (status == 404) {
                        throw new ReservationException(ErrorCode.PASS_NOT_FOUND);
                    }
                    if (status == 422) {
                        throw new ReservationException(ErrorCode.DURATION_NOT_AVAILABLE);
                    }
                    if (status >= 500) {
                        throw new IllegalStateException("upstream status " + status);
                    }
                    // 그 밖의 4xx는 재시도해도 같으므로 바로 실패로 본다.
                    log.warn("event=upstream_call target=u1 outcome=rejected status={}", status);
                    throw new ReservationException(ErrorCode.UPSTREAM_FAILED);
                });
    }

    private static boolean pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

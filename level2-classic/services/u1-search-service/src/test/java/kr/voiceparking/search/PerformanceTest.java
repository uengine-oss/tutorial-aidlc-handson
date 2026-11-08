package kr.voiceparking.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** NFR3.1, NFR3.2: 추천은 최대 2초, 주차권 조회는 최대 500ms, 동시 요청 10건도 모두 성공한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PerformanceTest {

    private static final Duration RECOMMEND_LIMIT = Duration.ofSeconds(2);
    private static final Duration LOOKUP_LIMIT = Duration.ofMillis(500);
    private static final int ITERATIONS = 100;

    @Autowired
    private MockMvc mvc;

    private int recommend() throws Exception {
        return mvc.perform(post("/api/search/recommendations")
                .header("X-Session-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"강남역 근처 2시간 주차권 찾아줘\"}"))
                .andReturn().getResponse().getStatus();
    }

    private int lookup() throws Exception {
        return mvc.perform(get("/internal/passes/gangnam-public-1").param("durationHours", "2"))
                .andReturn().getResponse().getStatus();
    }

    private static Duration slowest(Callable<Integer> call) throws Exception {
        Duration worst = Duration.ZERO;
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            assertThat(call.call()).isEqualTo(200);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            if (elapsed.compareTo(worst) > 0) {
                worst = elapsed;
            }
        }
        return worst;
    }

    @Test
    void recommendationStaysWithinTwoSecondsOverHundredRuns() throws Exception {
        recommend(); // 첫 호출의 클래스 로딩은 측정에서 뺀다.
        assertThat(slowest(this::recommend)).isLessThanOrEqualTo(RECOMMEND_LIMIT);
    }

    @Test
    void passLookupStaysWithinHalfASecondOverHundredRuns() throws Exception {
        lookup();
        assertThat(slowest(this::lookup)).isLessThanOrEqualTo(LOOKUP_LIMIT);
    }

    @Test
    void tenConcurrentRequestsAllSucceedWithinTheLimit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            long start = System.nanoTime();
            for (int i = 0; i < 10; i++) {
                results.add(pool.submit(i % 2 == 0 ? this::recommend : this::lookup));
            }
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThanOrEqualTo(RECOMMEND_LIMIT);
        } finally {
            pool.shutdownNow();
        }
    }
}

package kr.voiceparking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import kr.voiceparking.payment.support.StubServer;

/**
 * NFR1.5, E2: 실제 HTTP 클라이언트로 응답이 없거나 조금씩 흘리는 u2를 상대해도 결제 요청은 요청을 받은 때부터 1.7초
 * 안에 502 UPSTREAM_FAILED로 끝난다. 같은 키의 둘째 요청도 마찬가지다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UpstreamTimeoutTest {

    private static final StubServer U2;
    private static volatile boolean drip;

    static {
        try {
            U2 = new StubServer((exchange, index) -> {
                if (drip) {
                    exchange.sendResponseHeaders(201, 0);
                    for (int i = 0; i < 200; i++) {
                        exchange.getResponseBody().write('{');
                        exchange.getResponseBody().flush();
                        Thread.sleep(100);
                    }
                } else {
                    Thread.sleep(10_000);
                }
            });
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void u2Url(DynamicPropertyRegistry registry) {
        registry.add("reservation.service.url", U2::url);
    }

    @AfterAll
    static void stop() {
        U2.close();
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void mode() {
        drip = false;
    }

    private MvcResult pay(String session, String key) throws Exception {
        return mvc.perform(post("/api/payments").header("X-Session-Id", session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passId\":\"pass-1\",\"durationHours\":2,\"idempotencyKey\":\"" + key + "\"}"))
                .andReturn();
    }

    @Test
    void aSilentU2YieldsA502WithinOnePointSevenSeconds() throws Exception {
        long start = System.nanoTime();

        MvcResult result = pay(UUID.randomUUID().toString(), "k1");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(result.getResponse().getStatus()).isEqualTo(502);
        assertThat(result.getResponse().getContentAsString()).contains("UPSTREAM_FAILED");
        assertThat(elapsedMs).isLessThanOrEqualTo(1700L).isGreaterThanOrEqualTo(1300L);
    }

    @Test
    void aU2ThatDripsItsResponseIsCutOffAtTheSameDeadline() throws Exception {
        drip = true;
        long start = System.nanoTime();

        MvcResult result = pay(UUID.randomUUID().toString(), "k1");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(result.getResponse().getStatus()).isEqualTo(502);
        assertThat(elapsedMs).isLessThanOrEqualTo(1700L);
    }

    @Test
    void aSecondRequestWithTheSameKeyWhileTheFirstHangsAlsoEndsWithin1700MillisecondsOfItsOwnReceipt()
            throws Exception {
        String session = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Long> elapsed = Collections.synchronizedList(new ArrayList<>());
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(2);
        try {
            for (int i = 0; i < 2; i++) {
                int index = i;
                pool.submit(() -> {
                    try {
                        Thread.sleep(index * 150L);
                        long start = System.nanoTime();
                        MvcResult result = pay(session, "same-key");
                        elapsed.add((System.nanoTime() - start) / 1_000_000);
                        statuses.add(result.getResponse().getStatus());
                    } catch (Exception e) {
                        statuses.add(-1);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactly(502, 502);
        assertThat(elapsed).allSatisfy(ms -> assertThat(ms).isLessThanOrEqualTo(1700L));
    }
}

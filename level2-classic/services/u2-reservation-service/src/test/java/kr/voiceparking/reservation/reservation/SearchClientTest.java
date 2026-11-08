package kr.voiceparking.reservation.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** u1 호출(C4): 350ms 시도, 200ms 간격, 1.5초 마감, 최대 3회, 마감 안에 실패하면 502 (BR1.4, NFR1.4, NFR5.5). */
class SearchClientTest {

    private static final String OK_BODY = "{\"passId\":\"pass-1\",\"lotName\":\"강남 주차장\",\"durationHours\":2,\"price\":6000}";

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<Long> callTimes = Collections.synchronizedList(new ArrayList<>());
    private String lastPathAndQuery;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private HttpSearchClient start(IntFunction<Reply> behavior) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", exchange -> {
            int n = calls.incrementAndGet();
            callTimes.add(System.nanoTime());
            lastPathAndQuery = exchange.getRequestURI().toString();
            Reply reply = behavior.apply(n);
            try {
                if (reply.delayMs() > 0) {
                    Thread.sleep(reply.delayMs());
                }
                respond(exchange, reply.status(), reply.body());
            } catch (InterruptedException | IOException e) {
                // 클라이언트가 먼저 끊은 경우
            } finally {
                exchange.close();
            }
        });
        server.start();
        return new HttpSearchClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private record Reply(int status, String body, long delayMs) {
        static Reply ok() {
            return new Reply(200, OK_BODY, 0);
        }

        static Reply status(int status) {
            return new Reply(status, "{\"code\":\"X\",\"message\":\"x\"}", 0);
        }

        static Reply slow(long delayMs) {
            return new Reply(200, OK_BODY, delayMs);
        }
    }

    @Test
    void returnsPriceAndLotNameFromU1() throws Exception {
        HttpSearchClient client = start(n -> Reply.ok());

        PassInfo info = client.lookup("pass-1", 2);

        assertThat(info).isEqualTo(new PassInfo("pass-1", "강남 주차장", 2, 6000));
        assertThat(calls.get()).isEqualTo(1);
        assertThat(lastPathAndQuery).isEqualTo("/internal/passes/pass-1?durationHours=2");
    }

    @Test
    void passNotFoundIsNotRetried() throws Exception {
        HttpSearchClient client = start(n -> Reply.status(404));

        assertThatThrownBy(() -> client.lookup("nope", 2)).isInstanceOfSatisfying(ReservationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.PASS_NOT_FOUND));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void durationNotAvailableIsNotRetried() throws Exception {
        HttpSearchClient client = start(n -> Reply.status(422));

        assertThatThrownBy(() -> client.lookup("pass-1", 24)).isInstanceOfSatisfying(ReservationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.DURATION_NOT_AVAILABLE));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void serverErrorsAreRetriedWith200msGapUntilSuccess() throws Exception {
        HttpSearchClient client = start(n -> n < 3 ? Reply.status(500) : Reply.ok());

        PassInfo info = client.lookup("pass-1", 2);

        assertThat(info.price()).isEqualTo(6000);
        assertThat(calls.get()).isEqualTo(3);
        long gapMs = (callTimes.get(1) - callTimes.get(0)) / 1_000_000;
        assertThat(gapMs).isGreaterThanOrEqualTo(190);
    }

    @Test
    void anAttemptSlowerThan350msIsCutOffAndTheNextOneSucceeds() throws Exception {
        HttpSearchClient client = start(n -> n == 1 ? Reply.slow(1200) : Reply.ok());

        long start = System.nanoTime();
        PassInfo info = client.lookup("pass-1", 2);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(info.lotName()).isEqualTo("강남 주차장");
        assertThat(calls.get()).isEqualTo(2);
        assertThat(elapsedMs).isBetween(500L, 1000L);
    }

    @Test
    void persistentlySlowU1FailsWithin1500msAfterAtMostThreeCalls() throws Exception {
        HttpSearchClient client = start(n -> Reply.slow(3000));

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.lookup("pass-1", 2)).isInstanceOfSatisfying(ReservationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(calls.get()).isEqualTo(3);
        assertThat(elapsedMs).isBetween(1000L, 1700L);
    }

    @Test
    void unreachableU1FailsFastWithUpstreamFailed() throws Exception {
        HttpSearchClient client = start(n -> Reply.ok());
        server.stop(0);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.lookup("pass-1", 2)).isInstanceOfSatisfying(ReservationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(1700L);
    }

    @Test
    void unexpectedClientErrorFromU1BecomesUpstreamFailedWithoutRetry() throws Exception {
        HttpSearchClient client = start(n -> Reply.status(400));

        assertThatThrownBy(() -> client.lookup("pass-1", 2)).isInstanceOfSatisfying(ReservationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void malformedBodyBecomesUpstreamFailed() throws Exception {
        HttpSearchClient client = start(n -> new Reply(200, "not json", 0));

        assertThatThrownBy(() -> client.lookup("pass-1", 2)).isInstanceOfSatisfying(ReservationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED));
    }
}

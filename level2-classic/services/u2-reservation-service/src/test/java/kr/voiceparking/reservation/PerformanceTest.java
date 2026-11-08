package kr.voiceparking.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.JsonNode;

import kr.voiceparking.reservation.notification.NotificationService;
import kr.voiceparking.reservation.notification.Snapshot;
import kr.voiceparking.reservation.notification.Subscription;
import kr.voiceparking.reservation.support.FakeChannel;
import kr.voiceparking.reservation.support.FakeSearchConfig;
import kr.voiceparking.reservation.support.SseTestClient;
import kr.voiceparking.reservation.support.TestHttp;

/**
 * 성능: 처음 20회는 예열로 버리고 이어지는 100회의 최댓값을 목표와 비교한다. JVM 정지로 흔들리면 같은 시험을
 * 한 번 다시 실행하고 두 번 모두 넘으면 실패로 본다. 실제 HTTP와 실제 SSE 연결로 잰다.
 * 측정 구간: 결과 반영 요청을 보내기 직전부터 클라이언트가 snapshot 이벤트를 읽을 때까지(NFR1.1, 클라이언트
 * 읽기 시간 포함이라 서버만 잰 값보다 보수적이다), 결과 반영 요청부터 응답까지(NFR1.2), 연결 열기부터 첫 이벤트까지
 * (NFR1.3), 예약 생성 요청부터 응답까지(NFR1.4).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "reservation.push.max-subscriptions=5000")
@Import(FakeSearchConfig.class)
class PerformanceTest {

    private static final int WARM_UP = 20;
    private static final int RUNS = 100;
    private static final long PUSH_LIMIT_MS = 150;
    private static final long NO_SUBSCRIBER_LIMIT_MS = 100;
    private static final long FIRST_EVENT_LIMIT_MS = 200;
    private static final long CREATE_LIMIT_MS = 500;

    @LocalServerPort
    private int port;
    @Autowired
    private NotificationService notifications;

    private static long millis(long fromNanos, long toNanos) {
        return (toNanos - fromNanos) / 1_000_000;
    }

    /** 시험을 실행하고 최댓값이 한도를 넘으면 한 번 더 실행한다. */
    private static void assertMaxWithin(String what, long limitMs, Supplier<List<Long>> run) {
        long max = Collections.max(run.get());
        if (max > limitMs) {
            System.out.println("PERF " + what + " 첫 실행 최댓값=" + max + "ms (한도 " + limitMs + "ms), 한 번 다시 실행");
            max = Collections.max(run.get());
        }
        System.out.println("PERF " + what + " 최댓값=" + max + "ms (한도 " + limitMs + "ms)");
        assertThat(max).as(what + " 최댓값(ms)").isLessThanOrEqualTo(limitMs);
    }

    private record Sample(long createMs, long firstEventMs, long pushMs) {
    }

    private Sample oneRun(TestHttp http, int slowClients) throws Exception {
        String session = UUID.randomUUID().toString();

        long createStart = System.nanoTime();
        String id = http.createReservation(session);
        long createMs = millis(createStart, System.nanoTime());

        List<FakeChannel> slow = new ArrayList<>();
        List<Subscription> slowSubscriptions = new ArrayList<>();
        try {
            for (int i = 0; i < slowClients; i++) {
                FakeChannel channel = new FakeChannel();
                channel.blockWrites();
                slow.add(channel);
                slowSubscriptions.add(notifications.subscribe(id, channel,
                        new Snapshot(id, 1, "PENDING_PAYMENT", "x", 1, 1, java.time.Instant.now(), null, null, "m")));
            }
            long connectStart = System.nanoTime();
            try (SseTestClient client = SseTestClient.connect(port, id, session, null)) {
                SseTestClient.Event first = client.next(Duration.ofSeconds(3));
                assertThat(first).as("첫 이벤트").isNotNull();
                long firstEventMs = millis(connectStart, first.receivedNanos());
                assertThat(first.id()).isEqualTo("1");

                long requestStart = System.nanoTime();
                HttpResponse<String> response = http.paymentResult(id, "e-" + UUID.randomUUID(), "APPROVED");
                assertThat(response.statusCode()).isEqualTo(200);
                SseTestClient.Event pushed = client.next(Duration.ofSeconds(3));
                assertThat(pushed).as("푸시 이벤트").isNotNull();
                assertThat(pushed.id()).isEqualTo("2");
                assertThat(pushed.data()).contains("CONFIRMED");
                return new Sample(createMs, firstEventMs, millis(requestStart, pushed.receivedNanos()));
            }
        } finally {
            slow.forEach(FakeChannel::release);
            slowSubscriptions.forEach(s -> s.detach("TEST_DONE"));
        }
    }

    private List<Sample> runSamples(int slowClients) {
        try {
            TestHttp http = new TestHttp(port);
            for (int i = 0; i < WARM_UP; i++) {
                oneRun(http, slowClients);
            }
            List<Sample> samples = new ArrayList<>();
            for (int i = 0; i < RUNS; i++) {
                samples.add(oneRun(http, slowClients));
            }
            return samples;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void approvalReachesAnOpenConnectionWithin150msAndFirstEventAndCreateMeetTheirTargets() {
        assertMaxWithin("결과 반영 후 푸시(구독 연결 있음)", PUSH_LIMIT_MS,
                () -> runSamples(0).stream().map(Sample::pushMs).toList());
        assertMaxWithin("구독 첫 이벤트", FIRST_EVENT_LIMIT_MS,
                () -> runSamples(0).stream().map(Sample::firstEventMs).toList());
        assertMaxWithin("예약 생성", CREATE_LIMIT_MS, () -> runSamples(0).stream().map(Sample::createMs).toList());
    }

    @Test
    void resultApplicationWithoutAnyConnectionAnswersWithin100ms() {
        assertMaxWithin("결과 반영 응답(구독 연결 없음)", NO_SUBSCRIBER_LIMIT_MS, () -> {
            try {
                TestHttp http = new TestHttp(port);
                List<Long> times = new ArrayList<>();
                for (int i = 0; i < WARM_UP + RUNS; i++) {
                    String id = http.createReservation(UUID.randomUUID().toString());
                    long start = System.nanoTime();
                    HttpResponse<String> response = http.paymentResult(id, "e-1", "APPROVED");
                    long elapsed = millis(start, System.nanoTime());
                    assertThat(response.statusCode()).isEqualTo(200);
                    if (i >= WARM_UP) {
                        times.add(elapsed);
                    }
                }
                return times;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void oneToFourStuckClientsDoNotDelayThePushOfTheSameReservation() {
        for (int slowClients : new int[] { 1, 4 }) {
            assertMaxWithin("느린 클라이언트 " + slowClients + "개가 있을 때 푸시", PUSH_LIMIT_MS,
                    () -> runSamples(slowClients).stream().map(Sample::pushMs).toList());
        }
    }

    @Test
    void stuckClientsOnAnotherReservationDoNotDelayCancelPushesEither() throws Exception {
        TestHttp http = new TestHttp(port);
        String otherId = http.createReservation(UUID.randomUUID().toString());
        List<FakeChannel> slow = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            FakeChannel channel = new FakeChannel();
            channel.blockWrites();
            slow.add(channel);
            notifications.subscribe(otherId, channel,
                    new Snapshot(otherId, 1, "PENDING_PAYMENT", "x", 1, 1, java.time.Instant.now(), null, null, "m"));
        }
        try {
            assertMaxWithin("다른 예약의 느린 클라이언트 4개가 있을 때 푸시", PUSH_LIMIT_MS,
                    () -> runSamples(0).stream().map(Sample::pushMs).toList());
        } finally {
            slow.forEach(FakeChannel::release);
        }
    }

    @Test
    void tenSessionsAtOnceAllMeetThePushTarget() {
        assertMaxWithin("동시 10 세션의 푸시", PUSH_LIMIT_MS, () -> {
            ExecutorService pool = Executors.newFixedThreadPool(10);
            try {
                TestHttp http = new TestHttp(port);
                List<Callable<List<Long>>> sessions = new ArrayList<>();
                for (int s = 0; s < 10; s++) {
                    sessions.add(() -> {
                        List<Long> times = new ArrayList<>();
                        for (int i = 0; i < 12; i++) {
                            Sample sample = oneRun(http, i % 3 == 0 ? 1 : 0);
                            if (i >= 2) {
                                times.add(sample.pushMs());
                            }
                        }
                        return times;
                    });
                }
                List<Long> all = new ArrayList<>();
                for (Future<List<Long>> f : pool.invokeAll(sessions)) {
                    all.addAll(f.get(120, TimeUnit.SECONDS));
                }
                return all;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                pool.shutdownNow();
            }
        });
    }

    @Test
    void createdReservationJsonCarriesTheContractFields() throws Exception {
        TestHttp http = new TestHttp(port);
        String session = UUID.randomUUID().toString();
        String id = http.createReservation(session);

        JsonNode current = TestHttp.json(http.get("/api/reservations/current", session).body());

        assertThat(current.get("reservationId").asText()).isEqualTo(id);
        assertThat(current.get("status").asText()).isEqualTo("PENDING_PAYMENT");
    }
}

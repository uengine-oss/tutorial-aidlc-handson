package kr.voiceparking.reservation.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import kr.voiceparking.reservation.config.ReservationProperties.Push;
import kr.voiceparking.reservation.support.FakeChannel;

/** 알림: version 규칙, 구독 시 첫 이벤트, 연결별 순서, 실패 격리, 큐 한도, 쓰기 시간 한도, 구독 상한 (BR4.1~BR4.5). */
class NotificationServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-07T00:00:00Z");

    private final ThreadPoolExecutor pushExecutor =
            new ThreadPoolExecutor(0, 64, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private NotificationService service = serviceWith(new Push(32, 16, Duration.ofMillis(150),
            Duration.ofSeconds(15), Duration.ofMinutes(10)), pushExecutor);

    private NotificationService serviceWith(Push settings, Executor executor) {
        return new NotificationService(settings, executor, watchdog);
    }

    @AfterEach
    void shutDown() {
        pushExecutor.shutdownNow();
        watchdog.shutdownNow();
    }

    private static Snapshot snap(String id, int version) {
        return new Snapshot(id, version, version == 1 ? "PENDING_PAYMENT" : "CONFIRMED", "강남 주차장", 2, 6000, T0,
                null, null, "m");
    }

    @Test
    void subscribingSendsTheFallbackFirstAndThenFollowsPublishedSnapshots() {
        FakeChannel channel = new FakeChannel();
        service.subscribe("r-1", channel, snap("r-1", 1));
        service.publish(snap("r-1", 2), 0);

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(channel.versions()).containsExactly(1, 2));
    }

    @Test
    void firstEventIsTheStoredLatestSnapshotNotTheStaleFallback() {
        service.publish(snap("r-1", 1), 0);
        service.publish(snap("r-1", 2), 0);
        FakeChannel channel = new FakeChannel();

        service.subscribe("r-1", channel, snap("r-1", 1));

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(channel.versions()).containsExactly(2));
        assertThat(service.latest("r-1")).get().extracting(Snapshot::version).isEqualTo(2);
    }

    @Test
    void lowerOrEqualVersionsAreNeitherKeptNorSent() {
        FakeChannel channel = new FakeChannel();
        service.subscribe("r-1", channel, snap("r-1", 1));
        service.publish(snap("r-1", 3), 0);
        service.publish(snap("r-1", 2), 0);
        service.publish(snap("r-1", 3), 0);

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(channel.versions()).containsExactly(1, 3));
        assertThat(service.latest("r-1")).get().extracting(Snapshot::version).isEqualTo(3);
    }

    @Test
    void publishingWithoutSubscribersOnlyKeepsTheLatest() {
        service.publish(snap("r-1", 2), 0);

        assertThat(service.latest("r-1")).isPresent();
        assertThat(service.latest("other")).isEmpty();
        assertThat(service.subscriptionCount()).isZero();
    }

    @Test
    void rapidPublishesReachAConnectionInMonotonicOrder() {
        FakeChannel channel = new FakeChannel();
        service.subscribe("r-1", channel, snap("r-1", 1));
        for (int v = 2; v <= 15; v++) {
            service.publish(snap("r-1", v), 0);
        }

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(channel.versions()).hasSize(15));
        assertThat(channel.versions()).isSorted().doesNotHaveDuplicates();
        assertThat(channel.maxConcurrentWrites()).isEqualTo(1);
    }

    @Test
    void subscribingWhilePublishingNeverLosesOrReordersEvents() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 200; round++) {
                String id = "r-" + round;
                service.publish(snap(id, 1), 0);
                FakeChannel channel = new FakeChannel();
                CyclicBarrier start = new CyclicBarrier(2);
                var publisher = pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    for (int v = 2; v <= 6; v++) {
                        service.publish(snap(id, v), 0);
                    }
                    return null;
                });
                var subscriber = pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return service.subscribe(id, channel, snap(id, 1));
                });
                publisher.get(5, TimeUnit.SECONDS);
                Subscription subscription = subscriber.get(5, TimeUnit.SECONDS);

                await().pollInterval(Duration.ofMillis(1)).atMost(2, TimeUnit.SECONDS)
                        .until(() -> channel.versions().contains(6));
                List<Integer> versions = channel.versions();
                // 첫 이벤트는 등록 시점의 최신본이고, 이후 이벤트는 빠짐없이 순서대로 이어진다.
                List<Integer> expected = new ArrayList<>();
                for (int v = versions.get(0); v <= 6; v++) {
                    expected.add(v);
                }
                assertThat(versions).isEqualTo(expected);
                subscription.detach("TEST_DONE");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aWriteFailureRemovesOnlyThatConnection() {
        FakeChannel broken = new FakeChannel();
        FakeChannel healthy = new FakeChannel();
        broken.failWrites();
        service.subscribe("r-1", broken, snap("r-1", 1));
        service.subscribe("r-1", healthy, snap("r-1", 1));

        await().atMost(2, TimeUnit.SECONDS).until(broken::isClosed);
        service.publish(snap("r-1", 2), 0);

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(healthy.versions()).containsExactly(1, 2));
        assertThat(service.subscriptionCount()).isEqualTo(1);
        assertThat(broken.closedCause()).isEqualTo("WRITE_FAILED");
        assertThat(healthy.isClosed()).isFalse();
    }

    @Test
    void aQueueBeyond16ClosesOnlyTheSlowConnection() {
        FakeChannel slow = new FakeChannel();
        FakeChannel healthy = new FakeChannel();
        slow.blockWrites();
        service.subscribe("r-1", slow, snap("r-1", 1));
        service.subscribe("r-1", healthy, snap("r-1", 1));
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(healthy.versions()).hasSize(1));

        for (int v = 2; v <= 30; v++) {
            service.publish(snap("r-1", v), 0);
        }

        await().atMost(2, TimeUnit.SECONDS).until(slow::isClosed);
        assertThat(slow.closedCause()).isEqualTo("QUEUE_OVERFLOW");
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(healthy.versions()).contains(30));
        assertThat(healthy.isClosed()).isFalse();
        assertThat(service.subscriptionCount()).isEqualTo(1);
        slow.release();
    }

    @Test
    void aWriteLongerThanTheLimitClosesTheConnectionWithoutWaitingForTheBlockedThread() {
        FakeChannel stuck = new FakeChannel();
        FakeChannel healthy = new FakeChannel();
        stuck.blockWrites();
        service.subscribe("r-1", stuck, snap("r-1", 1));
        service.subscribe("r-1", healthy, snap("r-1", 1));
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(healthy.versions()).hasSize(1));

        await().pollDelay(Duration.ofMillis(250)).atMost(2, TimeUnit.SECONDS).until(() -> {
            service.checkSlowWrites();
            return stuck.isClosed();
        });

        assertThat(stuck.closedCause()).isEqualTo("WRITE_TIMEOUT");
        assertThat(service.subscriptionCount()).isEqualTo(1);
        service.publish(snap("r-1", 2), 0);
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(healthy.versions()).containsExactly(1, 2));
        stuck.release();
        // 풀려난 뒤에도 닫힌 연결에는 더 쓰지 않는다.
        assertThat(stuck.versions()).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void aFastWriteIsNotTreatedAsSlow() {
        FakeChannel channel = new FakeChannel();
        service.subscribe("r-1", channel, snap("r-1", 1));
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(channel.versions()).hasSize(1));

        service.checkSlowWrites();

        assertThat(channel.isClosed()).isFalse();
        assertThat(service.subscriptionCount()).isEqualTo(1);
    }

    @Test
    void keepAliveGoesThroughTheSameQueueAndNeverOverlapsAnEventWrite() {
        FakeChannel channel = new FakeChannel();
        channel.blockWrites();
        service.subscribe("r-1", channel, snap("r-1", 1));
        service.sendKeepAlives();
        service.publish(snap("r-1", 2), 0);
        channel.release();

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(channel.events())
                .containsExactly("snapshot:1", "keep-alive", "snapshot:2"));
        assertThat(channel.maxConcurrentWrites()).isEqualTo(1);
        assertThat(channel.keepAlives()).isEqualTo(1);
    }

    @Test
    void subscriptionsBeyondTheCapAreRefusedAndASlotFreesUpWhenAConnectionLeaves() {
        service = serviceWith(new Push(2, 16, Duration.ofSeconds(1), Duration.ofSeconds(15), Duration.ofMinutes(10)),
                pushExecutor);
        Subscription first = service.subscribe("r-1", new FakeChannel(), snap("r-1", 1));
        service.subscribe("r-1", new FakeChannel(), snap("r-1", 1));

        assertThatThrownBy(() -> service.subscribe("r-1", new FakeChannel(), snap("r-1", 1)))
                .isInstanceOf(SubscriptionLimitException.class);
        assertThat(service.subscriptionCount()).isEqualTo(2);

        first.detach("CLIENT_GONE");
        assertThat(service.subscriptionCount()).isEqualTo(1);
        service.subscribe("r-1", new FakeChannel(), snap("r-1", 1));
        assertThat(service.subscriptionCount()).isEqualTo(2);
    }

    @Test
    void detachingIsIdempotentAndStopsDelivery() {
        FakeChannel channel = new FakeChannel();
        Subscription subscription = service.subscribe("r-1", channel, snap("r-1", 1));
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(channel.versions()).hasSize(1));

        subscription.detach("CLIENT_GONE");
        subscription.detach("COMPLETED");
        service.publish(snap("r-1", 2), 0);

        assertThat(service.subscriptionCount()).isZero();
        assertThat(channel.versions()).containsExactly(1);
        // 클라이언트가 먼저 끊은 경우 채널을 다시 닫지 않는다.
        assertThat(channel.isClosed()).isFalse();
    }

    @Test
    void aRejectedDrainTaskClosesThatConnection() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("saturated");
        };
        service = serviceWith(Push.defaults(), rejecting);
        FakeChannel channel = new FakeChannel();

        service.subscribe("r-1", channel, snap("r-1", 1));

        await().atMost(2, TimeUnit.SECONDS).until(channel::isClosed);
        assertThat(channel.closedCause()).isEqualTo("EXECUTOR_REJECTED");
        assertThat(service.subscriptionCount()).isZero();
    }

    @Test
    void pushLogLineCarriesReservationVersionConnectionsAndElapsedMillis() {
        Logger logger = (Logger) LoggerFactory.getLogger(Connection.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            FakeChannel channel = new FakeChannel();
            service.subscribe("r-1", channel, snap("r-1", 1));
            service.publish(snap("r-1", 2), System.nanoTime());

            await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage))
                    .anyMatch(m -> m.matches("event=push reservation=r-1 version=2 connections=1 ms=\\d+")));
        } finally {
            logger.detachAppender(appender);
        }
    }
}

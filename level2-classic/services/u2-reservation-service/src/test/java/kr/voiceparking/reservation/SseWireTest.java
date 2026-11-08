package kr.voiceparking.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.JsonNode;

import kr.voiceparking.reservation.notification.NotificationService;
import kr.voiceparking.reservation.support.FakeSearchConfig;
import kr.voiceparking.reservation.support.SseTestClient;
import kr.voiceparking.reservation.support.TestHttp;

/** 실제 서버의 SSE 선 형식: 이벤트 이름, id, 연결 유지 주석, 오류 본문, 끊긴 연결 정리 (BR4.3, BR4.5, NFR4.4). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeSearchConfig.class)
class SseWireTest {

    @LocalServerPort
    private int port;
    @Autowired
    private NotificationService notifications;

    @Test
    void streamCarriesNamedSnapshotEventsWithVersionIdsAndKeepAliveComments() throws Exception {
        TestHttp http = new TestHttp(port);
        String session = UUID.randomUUID().toString();
        String id = http.createReservation(session);

        try (SseTestClient client = SseTestClient.connect(port, id, null, session)) {
            assertThat(client.statusCode()).isEqualTo(200);
            SseTestClient.Event first = client.next(Duration.ofSeconds(3));
            assertThat(first.name()).isEqualTo("snapshot");
            assertThat(first.id()).isEqualTo("1");
            JsonNode body = TestHttp.json(first.data());
            assertThat(body.get("reservationId").asText()).isEqualTo(id);
            assertThat(body.get("status").asText()).isEqualTo("PENDING_PAYMENT");
            assertThat(body.has("confirmedAt")).isFalse();

            notifications.sendKeepAlives();
            assertThat(client.nextComment(Duration.ofSeconds(3))).isEqualTo(": keep-alive");

            http.paymentResult(id, "e-1", "APPROVED");
            SseTestClient.Event second = client.next(Duration.ofSeconds(3));
            assertThat(second.id()).isEqualTo("2");
            assertThat(TestHttp.json(second.data()).get("status").asText()).isEqualTo("CONFIRMED");
        }
    }

    @Test
    void errorsOnTheSubscriptionEndpointAreJsonOverRealHttp() throws Exception {
        TestHttp http = new TestHttp(port);
        String session = UUID.randomUUID().toString();
        String id = http.createReservation(session);

        try (SseTestClient noSession = SseTestClient.connect(port, id, null, null)) {
            assertThat(noSession.statusCode()).isEqualTo(401);
        }
        try (SseTestClient stranger = SseTestClient.connect(port, id, UUID.randomUUID().toString(), null)) {
            assertThat(stranger.statusCode()).isEqualTo(404);
        }
        HttpResponse<String> response = http.get("/api/reservations/" + id + "/events", null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(TestHttp.json(response.body()).get("code").asText()).isEqualTo("MISSING_SESSION");
    }

    @Test
    void aClientThatDisconnectsIsRemovedAfterTheNextWriteFails() throws Exception {
        TestHttp http = new TestHttp(port);
        String session = UUID.randomUUID().toString();
        String id = http.createReservation(session);
        int before = notifications.subscriptionCount();

        SseTestClient client = SseTestClient.connect(port, id, null, session);
        assertThat(client.next(Duration.ofSeconds(3))).isNotNull();
        assertThat(notifications.subscriptionCount()).isEqualTo(before + 1);
        client.close();

        await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(100)).until(() -> {
            notifications.sendKeepAlives();
            return notifications.subscriptionCount() == before;
        });
    }
}

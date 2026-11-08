package kr.voiceparking.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.JsonNode;

import kr.voiceparking.reservation.support.FakeSearchConfig;
import kr.voiceparking.reservation.support.SseTestClient;
import kr.voiceparking.reservation.support.TestHttp;

/**
 * 마감을 2초로 줄여 실제 스케줄러 경로(타이머 → 취소 → 푸시)를 한 번 확인한다 (NFR2.1, NFR2.2, NFR2.4).
 * 주입 시계를 쓰는 경계 시험은 대기 없이 따로 있다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "reservation.payment-timeout=2s")
@Import(FakeSearchConfig.class)
class DeadlineSchedulerPathTest {

    @LocalServerPort
    private int port;

    @Test
    void withoutApprovalTheRealTimerCancelsAndPushesTheNotice() throws Exception {
        TestHttp http = new TestHttp(port);
        String session = UUID.randomUUID().toString();
        String id = http.createReservation(session);

        try (SseTestClient client = SseTestClient.connect(port, id, session, null)) {
            SseTestClient.Event first = client.next(Duration.ofSeconds(3));
            assertThat(first.id()).isEqualTo("1");
            JsonNode pending = TestHttp.json(first.data());
            Instant requestedAt = Instant.parse(pending.get("paymentRequestedAt").asText());

            SseTestClient.Event cancelled = client.next(Duration.ofSeconds(6));

            assertThat(cancelled).as("취소 푸시").isNotNull();
            JsonNode body = TestHttp.json(cancelled.data());
            assertThat(cancelled.id()).isEqualTo("2");
            assertThat(body.get("status").asText()).isEqualTo("CANCELLED");
            assertThat(body.get("message").asText()).isEqualTo("승인 지연으로 예약이 취소되었습니다");
            Duration afterRequest = Duration.between(requestedAt, Instant.parse(body.get("cancelledAt").asText()));
            assertThat(afterRequest).isBetween(Duration.ofMillis(1900), Duration.ofMillis(4000));
        }
    }

    @Test
    void anApprovalBeforeTheShortDeadlineWinsAndTheLaterTimerChangesNothing() throws Exception {
        TestHttp http = new TestHttp(port);
        String session = UUID.randomUUID().toString();
        String id = http.createReservation(session);

        assertThat(TestHttp.json(http.paymentResult(id, "e-1", "APPROVED").body()).get("result").asText())
                .isEqualTo("APPLIED");
        Thread.sleep(2600);

        JsonNode current = TestHttp.json(http.get("/api/reservations/" + id, session).body());
        assertThat(current.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(current.get("version").asInt()).isEqualTo(2);
    }

    @Test
    void anApprovalAfterTheDeadlineIsRejectedEvenIfTheTimerHasNotCancelledYet() throws Exception {
        TestHttp http = new TestHttp(port);
        String session = UUID.randomUUID().toString();
        String id = http.createReservation(session);
        Thread.sleep(2100);

        JsonNode result = TestHttp.json(http.paymentResult(id, "e-late", "APPROVED").body());

        assertThat(result.get("result").asText()).isEqualTo("REJECTED");
        assertThat(result.get("reservationStatus").asText()).isEqualTo("CANCELLED");
    }
}

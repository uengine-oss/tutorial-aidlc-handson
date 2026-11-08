package kr.voiceparking.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.JsonNode;

import kr.voiceparking.reservation.support.FakeSearchConfig;
import kr.voiceparking.reservation.support.SseTestClient;
import kr.voiceparking.reservation.support.TestHttp;

/**
 * 실제 180초 기다리는 취소 시험. 일상 실행에서는 제외하고(slow) 병합 전 검증(-Pslow)에서만 돈다.
 * 결제 요청 시점부터 178~182초 안에 취소되고 안내 문구가 붙는지 확인한다 (BR2.4, AC4.2.4).
 */
@Tag("slow")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeSearchConfig.class)
class RealDeadlineSlowTest {

    @LocalServerPort
    private int port;

    @Test
    void cancelsBetween178And182SecondsAfterThePaymentRequest() throws Exception {
        TestHttp http = new TestHttp(port);
        String session = UUID.randomUUID().toString();
        String id = http.createReservation(session);

        try (SseTestClient client = SseTestClient.connect(port, id, session, null)) {
            JsonNode pending = TestHttp.json(client.next(Duration.ofSeconds(3)).data());
            Instant requestedAt = Instant.parse(pending.get("paymentRequestedAt").asText());

            SseTestClient.Event cancelled = client.next(Duration.ofSeconds(190));

            assertThat(cancelled).as("취소 푸시").isNotNull();
            JsonNode body = TestHttp.json(cancelled.data());
            assertThat(body.get("status").asText()).isEqualTo("CANCELLED");
            assertThat(body.get("message").asText()).isEqualTo("승인 지연으로 예약이 취소되었습니다");
            Duration afterRequest = Duration.between(requestedAt, Instant.parse(body.get("cancelledAt").asText()));
            assertThat(afterRequest).isBetween(Duration.ofSeconds(178), Duration.ofSeconds(182));
        }
    }
}

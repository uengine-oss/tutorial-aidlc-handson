package kr.voiceparking.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.voiceparking.payment.support.StubServer;
import kr.voiceparking.payment.support.StubServer.Hit;

/** u2 호출(C5): 예약 생성은 재시도 없음·총 소요 시간 제한, 결과 반영은 시도당 제한·간격·최대 횟수 (NFR5.3, NFR5.4, E2). */
class HttpReservationClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CreateCall CALL = new CreateCall("11111111-2222-3333-4444-555555555555", "pass-1", 2, "key-1");
    private static final String CREATED = "{\"reservationId\":\"res-1\",\"status\":\"PENDING_PAYMENT\",\"amount\":4000,"
            + "\"durationHours\":2,\"lotName\":\"강남 공영주차장\",\"paymentRequestedAt\":\"2026-11-05T01:00:00Z\"}";

    private static PaymentException failureOf(Runnable action) {
        try {
            action.run();
        } catch (PaymentException e) {
            return e;
        }
        throw new AssertionError("expected a PaymentException");
    }

    @Test
    void theDefaultTimingsAreTwoSecondsPerAttemptTwoHundredMsGapAndThreeAttempts() {
        assertThat(HttpReservationClient.APPLY_ATTEMPT_TIMEOUT).isEqualTo(Duration.ofSeconds(2));
        assertThat(HttpReservationClient.APPLY_RETRY_GAP).isEqualTo(Duration.ofMillis(200));
        assertThat(HttpReservationClient.APPLY_MAX_ATTEMPTS).isEqualTo(3);
    }

    @Test
    void a201MeansANewReservationAndTheRequestBodyCarriesTheFourFields() throws Exception {
        try (StubServer u2 = StubServer.replying(201, CREATED)) {
            ReservationReply reply = new HttpReservationClient(u2.url()).create(CALL, Duration.ofSeconds(1));

            assertThat(reply.created()).isTrue();
            assertThat(reply.reservationId()).isEqualTo("res-1");
            assertThat(reply.status()).isEqualTo("PENDING_PAYMENT");
            assertThat(reply.amount()).isEqualTo(4000);
            assertThat(reply.durationHours()).isEqualTo(2);
            assertThat(reply.lotName()).isEqualTo("강남 공영주차장");
            assertThat(reply.paymentRequestedAt()).isEqualTo(Instant.parse("2026-11-05T01:00:00Z"));
            Hit hit = u2.hits().get(0);
            assertThat(hit.method()).isEqualTo("POST");
            assertThat(hit.path()).isEqualTo("/internal/reservations");
            JsonNode body = JSON.readTree(hit.body());
            assertThat(body.get("sessionId").asText()).isEqualTo(CALL.sessionId());
            assertThat(body.get("passId").asText()).isEqualTo("pass-1");
            assertThat(body.get("durationHours").asInt()).isEqualTo(2);
            assertThat(body.get("idempotencyKey").asText()).isEqualTo("key-1");
        }
    }

    @Test
    void a200MeansAnExistingReservationWhoseStatusMayBeAnyState() throws Exception {
        String ended = CREATED.replace("PENDING_PAYMENT", "CONFIRMED");
        try (StubServer u2 = StubServer.replying(200, ended)) {
            ReservationReply reply = new HttpReservationClient(u2.url()).create(CALL, Duration.ofSeconds(1));

            assertThat(reply.created()).isFalse();
            assertThat(reply.status()).isEqualTo("CONFIRMED");
        }
    }

    @Test
    void rejectionsKeepTheirCodeAndAConflictKeepsTheExistingReservationId() throws Exception {
        try (StubServer notFound = StubServer.replying(404, "{\"code\":\"PASS_NOT_FOUND\",\"message\":\"x\"}");
                StubServer conflict = StubServer.replying(409,
                        "{\"code\":\"PENDING_EXISTS\",\"message\":\"x\",\"reservationId\":\"res-9\"}");
                StubServer unavailable = StubServer.replying(422, "{\"code\":\"DURATION_NOT_AVAILABLE\"}")) {
            PaymentException a = failureOf(() -> new HttpReservationClient(notFound.url()).create(CALL,
                    Duration.ofSeconds(1)));
            PaymentException b = failureOf(() -> new HttpReservationClient(conflict.url()).create(CALL,
                    Duration.ofSeconds(1)));
            PaymentException c = failureOf(() -> new HttpReservationClient(unavailable.url()).create(CALL,
                    Duration.ofSeconds(1)));

            assertThat(a.errorCode()).isEqualTo(ErrorCode.PASS_NOT_FOUND);
            assertThat(b.errorCode()).isEqualTo(ErrorCode.PENDING_EXISTS);
            assertThat(b.reservationId()).isEqualTo("res-9");
            assertThat(c.errorCode()).isEqualTo(ErrorCode.DURATION_NOT_AVAILABLE);
        }
    }

    @Test
    void otherStatusesAreUpstreamFailuresAndTheCreateCallIsNeverRetried() throws Exception {
        for (int status : new int[] { 400, 500, 502, 503 }) {
            try (StubServer u2 = StubServer.replying(status, "{\"code\":\"X\"}")) {
                PaymentException error = failureOf(() -> new HttpReservationClient(u2.url()).create(CALL,
                        Duration.ofSeconds(1)));

                assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
                assertThat(u2.hitCount()).isEqualTo(1);
            }
        }
    }

    @Test
    void aMalformedSuccessBodyIsAnUpstreamFailure() throws Exception {
        try (StubServer u2 = StubServer.replying(201, "not json")) {
            assertThat(failureOf(() -> new HttpReservationClient(u2.url()).create(CALL, Duration.ofSeconds(1)))
                    .errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
        }
    }

    @Test
    void aRefusedConnectionFailsFastAsUpstreamFailed() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        long start = System.nanoTime();

        PaymentException error = failureOf(() -> new HttpReservationClient("http://127.0.0.1:" + closedPort)
                .create(CALL, Duration.ofSeconds(1)));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(800L);
    }

    @Test
    void aSilentServerIsCutOffAtTheGivenTotalTime() throws Exception {
        try (StubServer u2 = new StubServer((exchange, index) -> Thread.sleep(5000))) {
            long start = System.nanoTime();

            PaymentException error = failureOf(() -> new HttpReservationClient(u2.url()).create(CALL,
                    Duration.ofMillis(500)));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
            assertThat(elapsedMs).isBetween(450L, 800L);
            assertThat(u2.hitCount()).isEqualTo(1);
        }
    }

    @Test
    void aServerThatDripsTheResponseIsStillCutOffAtTheTotalTime() throws Exception {
        try (StubServer u2 = new StubServer((exchange, index) -> {
            exchange.sendResponseHeaders(201, 0);
            for (int i = 0; i < 100; i++) {
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                Thread.sleep(100);
            }
        })) {
            long start = System.nanoTime();

            PaymentException error = failureOf(() -> new HttpReservationClient(u2.url()).create(CALL,
                    Duration.ofMillis(600)));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
            assertThat(elapsedMs).isBetween(550L, 900L);
        }
    }

    @Test
    void aNonPositiveTimeoutFailsWithoutCallingU2() throws Exception {
        try (StubServer u2 = StubServer.replying(201, CREATED)) {
            assertThat(failureOf(() -> new HttpReservationClient(u2.url()).create(CALL, Duration.ZERO)).errorCode())
                    .isEqualTo(ErrorCode.UPSTREAM_FAILED);
            assertThat(u2.hitCount()).isZero();
        }
    }

    @Test
    void anAppliedResultIsParsedAndTheRequestCarriesEventIdAndOutcome() throws Exception {
        try (StubServer u2 = StubServer.replying(200, "{\"result\":\"APPLIED\",\"reservationStatus\":\"CONFIRMED\"}")) {
            ApplyReply reply = new HttpReservationClient(u2.url()).applyPaymentResult("res-1", "evt-1",
                    Outcome.APPROVED);

            assertThat(reply).isEqualTo(new ApplyReply(ApplyResult.APPLIED, "CONFIRMED"));
            Hit hit = u2.hits().get(0);
            assertThat(hit.path()).isEqualTo("/internal/reservations/res-1/payment-result");
            JsonNode body = JSON.readTree(hit.body());
            assertThat(body.get("eventId").asText()).isEqualTo("evt-1");
            assertThat(body.get("outcome").asText()).isEqualTo("APPROVED");
        }
    }

    @Test
    void aRejectedResultIsParsed() throws Exception {
        try (StubServer u2 = StubServer.replying(200, "{\"result\":\"REJECTED\",\"reservationStatus\":\"CANCELLED\"}")) {
            assertThat(new HttpReservationClient(u2.url()).applyPaymentResult("res-1", "evt-1", Outcome.FAILED))
                    .isEqualTo(new ApplyReply(ApplyResult.REJECTED, "CANCELLED"));
        }
    }

    @Test
    void aTransientFailureIsRetriedAfterTheGapAndASuccessfulRetryWins() throws Exception {
        try (StubServer u2 = new StubServer((exchange, index) -> {
            if (index < 2) {
                StubServer.respond(exchange, 503, "{}");
            } else {
                StubServer.respond(exchange, 200, "{\"result\":\"APPLIED\",\"reservationStatus\":\"CONFIRMED\"}");
            }
        })) {
            ApplyReply reply = new HttpReservationClient(u2.url()).applyPaymentResult("res-1", "evt-1",
                    Outcome.APPROVED);

            assertThat(reply.result()).isEqualTo(ApplyResult.APPLIED);
            List<Hit> hits = u2.hits();
            assertThat(hits).hasSize(3);
            assertThat((hits.get(1).nanos() - hits.get(0).nanos()) / 1_000_000).isGreaterThanOrEqualTo(180L);
            assertThat((hits.get(2).nanos() - hits.get(1).nanos()) / 1_000_000).isGreaterThanOrEqualTo(180L);
            for (Hit hit : hits) {
                assertThat(JSON.readTree(hit.body()).get("eventId").asText()).isEqualTo("evt-1");
            }
        }
    }

    @Test
    void afterThreeFailedAttemptsTheResultIsUpstreamFailed() throws Exception {
        try (StubServer u2 = StubServer.replying(503, "{}")) {
            PaymentException error = failureOf(() -> new HttpReservationClient(u2.url()).applyPaymentResult(
                    "res-1", "evt-1", Outcome.APPROVED));

            assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
            assertThat(u2.hitCount()).isEqualTo(3);
        }
    }

    @Test
    void definitiveRejectionsAreNotRetried() throws Exception {
        for (int status : new int[] { 400, 404 }) {
            try (StubServer u2 = StubServer.replying(status, "{\"code\":\"X\"}")) {
                PaymentException error = failureOf(() -> new HttpReservationClient(u2.url()).applyPaymentResult(
                        "res-1", "evt-1", Outcome.APPROVED));

                assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
                assertThat(u2.hitCount()).isEqualTo(1);
            }
        }
    }

    @Test
    void eachAttemptIsCutOffAtItsOwnTimeLimit() throws Exception {
        try (StubServer u2 = new StubServer((exchange, index) -> Thread.sleep(3000))) {
            HttpReservationClient client = new HttpReservationClient(u2.url(), Duration.ofMillis(300),
                    Duration.ofMillis(50), 3);
            long start = System.nanoTime();

            PaymentException error = failureOf(() -> client.applyPaymentResult("res-1", "evt-1", Outcome.APPROVED));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(error.errorCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED);
            assertThat(u2.hitCount()).isEqualTo(3);
            assertThat(elapsedMs).isBetween(1000L, 1500L);
        }
    }

    @Test
    void aDrippingResponseIsCutOffPerAttemptToo() throws Exception {
        try (StubServer u2 = new StubServer((exchange, index) -> {
            exchange.sendResponseHeaders(200, 0);
            for (int i = 0; i < 100; i++) {
                exchange.getResponseBody().write(' ');
                exchange.getResponseBody().flush();
                Thread.sleep(50);
            }
        })) {
            HttpReservationClient client = new HttpReservationClient(u2.url(), Duration.ofMillis(300),
                    Duration.ofMillis(50), 2);
            long start = System.nanoTime();

            assertThatThrownBy(() -> client.applyPaymentResult("res-1", "evt-1", Outcome.APPROVED))
                    .isInstanceOf(PaymentException.class);

            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1000L);
        }
    }

    @Test
    void anUnknownResultValueIsAnUpstreamFailure() throws Exception {
        try (StubServer u2 = StubServer.replying(200, "{\"result\":\"SOMETHING\",\"reservationStatus\":\"X\"}")) {
            assertThat(failureOf(() -> new HttpReservationClient(u2.url(), Duration.ofSeconds(1),
                    Duration.ofMillis(10), 1).applyPaymentResult("res-1", "evt-1", Outcome.APPROVED)).errorCode())
                    .isEqualTo(ErrorCode.UPSTREAM_FAILED);
        }
    }
}

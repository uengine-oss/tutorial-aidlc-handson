package kr.voiceparking.reservation.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 실제 서버에 JSON을 보내는 시험용 도우미. */
public final class TestHttp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2)).build();

    private final int port;

    public TestHttp(int port) {
        this.port = port;
    }

    public HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> get(String path, String sessionHeader) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (sessionHeader != null) {
            request.header("X-Session-Id", sessionHeader);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 새 세션으로 예약을 만들고 reservationId를 돌려준다. */
    public String createReservation(String sessionId) throws IOException, InterruptedException {
        String body = "{\"sessionId\":\"" + sessionId + "\",\"passId\":\"pass-1\",\"durationHours\":2,"
                + "\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}";
        HttpResponse<String> response = post("/internal/reservations", body);
        if (response.statusCode() != 201) {
            throw new IllegalStateException("create failed: " + response.statusCode() + " " + response.body());
        }
        return json(response.body()).get("reservationId").asText();
    }

    public HttpResponse<String> paymentResult(String reservationId, String eventId, String outcome)
            throws IOException, InterruptedException {
        return post("/internal/reservations/" + reservationId + "/payment-result",
                "{\"eventId\":\"" + eventId + "\",\"outcome\":\"" + outcome + "\"}");
    }

    public static JsonNode json(String body) throws IOException {
        return MAPPER.readTree(body);
    }
}

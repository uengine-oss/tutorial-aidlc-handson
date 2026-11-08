package kr.voiceparking.reservation.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** 실제 HTTP로 SSE를 읽는 시험용 클라이언트. 이벤트 하나가 완성된 시각(nanoTime)을 함께 기록한다. */
public final class SseTestClient implements AutoCloseable {

    public record Event(String name, String id, String data, long receivedNanos) {
    }

    private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> comments = new LinkedBlockingQueue<>();
    private final InputStream stream;
    private final int statusCode;
    private final Thread reader;

    private SseTestClient(HttpResponse<InputStream> response) {
        this.statusCode = response.statusCode();
        this.stream = response.body();
        this.reader = new Thread(this::readLoop, "sse-test-reader");
        this.reader.setDaemon(true);
        this.reader.start();
    }

    /** 연결을 열고 응답 헤더를 받을 때까지 기다린다. */
    public static SseTestClient connect(int port, String reservationId, String sessionHeader, String sessionQuery)
            throws IOException, InterruptedException {
        String query = sessionQuery == null ? "" : "?sessionId=" + sessionQuery;
        HttpRequest.Builder request = HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:" + port + "/api/reservations/" + reservationId + "/events"
                        + query))
                .header("Accept", "text/event-stream").GET();
        if (sessionHeader != null) {
            request.header("X-Session-Id", sessionHeader);
        }
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2)).build();
        return new SseTestClient(client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream()));
    }

    public int statusCode() {
        return statusCode;
    }

    /** 다음 이벤트. 시간 안에 오지 않으면 null이다. */
    public Event next(Duration timeout) throws InterruptedException {
        return events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 다음 주석 줄(예: ": keep-alive"). 시간 안에 오지 않으면 null이다. */
    public String nextComment(Duration timeout) throws InterruptedException {
        return comments.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void readLoop() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String name = null;
            String id = null;
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    if (name != null || data.length() > 0) {
                        events.add(new Event(name, id, data.toString(), System.nanoTime()));
                    }
                    name = null;
                    id = null;
                    data.setLength(0);
                } else if (line.startsWith(":")) {
                    comments.add(line);
                } else if (line.startsWith("event:")) {
                    name = line.substring("event:".length()).trim();
                } else if (line.startsWith("id:")) {
                    id = line.substring("id:".length()).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring("data:".length()).trim());
                }
            }
        } catch (IOException e) {
            // 연결이 닫혔다.
        }
    }

    @Override
    public void close() {
        try {
            stream.close();
        } catch (IOException e) {
            // 이미 닫힌 연결
        }
    }
}

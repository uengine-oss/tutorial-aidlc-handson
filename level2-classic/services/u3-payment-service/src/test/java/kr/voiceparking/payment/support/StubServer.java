package kr.voiceparking.payment.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** 루프백에서 도는 시험용 HTTP 서버. 요청마다 주어진 동작을 실행하고 받은 요청을 기록한다. */
public final class StubServer implements AutoCloseable {

    /** 요청 하나에 대한 응답 동작. index는 0부터 센 도착 순서다. */
    @FunctionalInterface
    public interface Behavior {
        void handle(HttpExchange exchange, int index) throws Exception;
    }

    public record Hit(String method, String path, String body, String signature, long nanos) {
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Hit> hits = Collections.synchronizedList(new ArrayList<>());

    public StubServer(Behavior behavior) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            int index;
            synchronized (hits) {
                index = hits.size();
                hits.add(new Hit(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        read(exchange.getRequestBody()), exchange.getRequestHeaders().getFirst("X-Signature"),
                        System.nanoTime()));
            }
            try {
                behavior.handle(exchange, index);
            } catch (Exception e) {
                // 클라이언트가 먼저 끊은 경우 등. 시험 서버는 조용히 닫는다.
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    public static StubServer replying(int status, String body) throws IOException {
        return new StubServer((exchange, index) -> respond(exchange, status, body));
    }

    public static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String url() {
        return "http://127.0.0.1:" + port();
    }

    public List<Hit> hits() {
        synchronized (hits) {
            return new ArrayList<>(hits);
        }
    }

    public int hitCount() {
        return hits.size();
    }

    private static String read(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}

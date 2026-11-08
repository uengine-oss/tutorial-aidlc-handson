package kr.voiceparking.payment.payment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * u2 호출(C5)을 JDK HttpClient로 한다. 재시도 라이브러리는 쓰지 않는다.
 *
 * <p>예약 생성: 시도 1회, 재시도 없음. 호출 전체(연결, 응답 머리, 본문 읽기)가 받은 제한 시간을 넘으면 요청을 취소하고
 * 실패로 본다. 연결·읽기 제한만 걸면 응답을 조금씩 흘리는 서버가 제한을 계속 늘리므로 비동기 호출을 총 시간으로 끊는다 (E2).
 *
 * <p>결과 반영: 시도당 총 2초, 간격 200ms, 최대 3회. 같은 eventId라 재시도해도 안전하다. 연결 실패·시간 초과·5xx만
 * 재시도하고, 400·404 같은 확정된 거절은 재시도해도 같으므로 바로 실패로 본다.
 */
public class HttpReservationClient implements ReservationClient {

    public static final Duration APPLY_ATTEMPT_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration APPLY_RETRY_GAP = Duration.ofMillis(200);
    public static final int APPLY_MAX_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(HttpReservationClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 재시도해 볼 만한 실패(연결, 시간 초과, 5xx, 읽을 수 없는 본문). */
    private static final class TransientFailure extends RuntimeException {
        TransientFailure(String message, Throwable cause) {
            super(message, cause, false, false);
        }
    }

    private final String baseUrl;
    private final HttpClient http;
    private final Duration attemptTimeout;
    private final Duration retryGap;
    private final int maxAttempts;

    public HttpReservationClient(String baseUrl) {
        this(baseUrl, APPLY_ATTEMPT_TIMEOUT, APPLY_RETRY_GAP, APPLY_MAX_ATTEMPTS);
    }

    public HttpReservationClient(String baseUrl, Duration attemptTimeout, Duration retryGap, int maxAttempts) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2)).build();
        this.attemptTimeout = attemptTimeout;
        this.retryGap = retryGap;
        this.maxAttempts = maxAttempts;
    }

    // ---------------------------------------------------------------- 예약 생성

    @Override
    public ReservationReply create(CreateCall call, Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new PaymentException(ErrorCode.UPSTREAM_FAILED);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", call.sessionId());
        body.put("passId", call.passId());
        body.put("durationHours", call.durationHours());
        body.put("idempotencyKey", call.idempotencyKey());
        HttpResponse<String> response;
        try {
            response = exchange(post("/internal/reservations", body, timeout), timeout);
        } catch (TransientFailure e) {
            log.warn("event=upstream_call target=u2 op=create outcome=failed cause={}", causeOf(e));
            throw new PaymentException(ErrorCode.UPSTREAM_FAILED);
        }
        int status = response.statusCode();
        switch (status) {
            case 201, 200 -> {
                return parseReservation(response.body(), status == 201);
            }
            case 404 -> throw new PaymentException(ErrorCode.PASS_NOT_FOUND);
            case 409 -> throw new PaymentException(ErrorCode.PENDING_EXISTS, existingReservationId(response.body()));
            case 422 -> throw new PaymentException(ErrorCode.DURATION_NOT_AVAILABLE);
            default -> {
                log.warn("event=upstream_call target=u2 op=create outcome=rejected status={}", status);
                throw new PaymentException(ErrorCode.UPSTREAM_FAILED);
            }
        }
    }

    private static ReservationReply parseReservation(String body, boolean created) {
        try {
            JsonNode node = JSON.readTree(body);
            return new ReservationReply(created, requiredText(node, "reservationId"), requiredText(node, "status"),
                    node.get("amount").intValue(), node.get("durationHours").intValue(),
                    node.hasNonNull("lotName") ? node.get("lotName").asText() : null,
                    Instant.parse(requiredText(node, "paymentRequestedAt")));
        } catch (Exception e) {
            log.warn("event=upstream_call target=u2 op=create outcome=unreadable cause={}",
                    e.getClass().getSimpleName());
            throw new PaymentException(ErrorCode.UPSTREAM_FAILED);
        }
    }

    private static String existingReservationId(String body) {
        try {
            JsonNode id = JSON.readTree(body).get("reservationId");
            return id != null && id.isTextual() ? id.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalStateException("missing " + field);
        }
        return value.asText();
    }

    // ---------------------------------------------------------------- 결과 반영

    @Override
    public ApplyReply applyPaymentResult(String reservationId, String eventId, Outcome outcome) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("outcome", outcome.name());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return applyOnce(reservationId, body);
            } catch (TransientFailure e) {
                log.warn("event=upstream_call target=u2 op=payment_result attempt={} outcome=failed cause={}",
                        attempt, causeOf(e));
            }
            if (attempt < maxAttempts && !pause(retryGap)) {
                break;
            }
        }
        throw new PaymentException(ErrorCode.UPSTREAM_FAILED);
    }

    private ApplyReply applyOnce(String reservationId, Map<String, Object> body) {
        HttpResponse<String> response = exchange(post("/internal/reservations/" + encode(reservationId)
                + "/payment-result", body, attemptTimeout), attemptTimeout);
        int status = response.statusCode();
        if (status >= 500) {
            throw new TransientFailure("upstream status " + status, null);
        }
        if (status != 200) {
            log.warn("event=upstream_call target=u2 op=payment_result outcome=rejected status={}", status);
            throw new PaymentException(ErrorCode.UPSTREAM_FAILED);
        }
        try {
            JsonNode node = JSON.readTree(response.body());
            return new ApplyReply(ApplyResult.valueOf(requiredText(node, "result")),
                    requiredText(node, "reservationStatus"));
        } catch (Exception e) {
            log.warn("event=upstream_call target=u2 op=payment_result outcome=unreadable cause={}",
                    e.getClass().getSimpleName());
            throw new PaymentException(ErrorCode.UPSTREAM_FAILED);
        }
    }

    // ---------------------------------------------------------------- 공통

    private HttpRequest post(String path, Map<String, Object> body, Duration timeout) {
        try {
            return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
        } catch (Exception e) {
            throw new IllegalStateException("cannot build request", e);
        }
    }

    /** 호출 전체를 총 시간으로 끊는다. 넘으면 요청을 취소해 연결을 닫는다. */
    private HttpResponse<String> exchange(HttpRequest request, Duration total) {
        CompletableFuture<HttpResponse<String>> future = http.sendAsync(request,
                HttpResponse.BodyHandlers.ofString());
        try {
            return future.get(total.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TransientFailure("total time exceeded", e);
        } catch (ExecutionException e) {
            throw new TransientFailure("call failed", e.getCause());
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new TransientFailure("interrupted", e);
        }
    }

    private static String causeOf(TransientFailure e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return cause.getClass().getSimpleName();
    }

    private static String encode(String pathSegment) {
        return java.net.URLEncoder.encode(pathSegment, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

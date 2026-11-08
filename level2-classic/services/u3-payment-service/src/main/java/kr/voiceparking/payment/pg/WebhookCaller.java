package kr.voiceparking.payment.pg;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/** 모의 PG가 이 서비스의 웹훅 주소를 실제 HTTP로 부르는 호출기. 제한 시간 2초, 다시 보내지 않는다 (BR2.1). */
public class WebhookCaller {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    /** 호출 실패의 원인 구분 (E4). */
    public enum Cause {
        /** 연결 거부, 오류 응답, 그 밖의 오류 */
        FAILED,
        /** 응답 시간 초과: 서버 처리가 이어져 적용됐을 수 있다 */
        NO_RESPONSE
    }

    public static final class CallException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final transient Cause failure;

        CallException(Cause failure, String message) {
            super(message, null, false, false);
            this.failure = failure;
        }

        public Cause failure() {
            return failure;
        }
    }

    private final URI url;
    private final Duration timeout;
    private final HttpClient http;

    public WebhookCaller(String url, Duration timeout) {
        this.url = URI.create(url);
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build();
    }

    /** 서명된 본문을 한 번 보낸다. 2xx가 아니거나 실패하면 {@link CallException}을 던진다. */
    public void post(byte[] body, String signature) {
        HttpRequest request = HttpRequest.newBuilder(url).timeout(timeout)
                .header("Content-Type", "application/json").header("X-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<Void> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (HttpConnectTimeoutException e) {
            throw new CallException(Cause.FAILED, "connect timeout");
        } catch (HttpTimeoutException e) {
            throw new CallException(Cause.NO_RESPONSE, "no response in time");
        } catch (IOException e) {
            throw new CallException(Cause.FAILED, e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CallException(Cause.FAILED, "interrupted");
        }
        if (response.statusCode() / 100 != 2) {
            throw new CallException(Cause.FAILED, "status " + response.statusCode());
        }
    }
}

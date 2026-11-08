package kr.voiceparking.reservation.notification;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SseEmitter를 {@link EventChannel}로 감싼다. 이벤트 이름 snapshot, id = version, 데이터 = 스냅샷 JSON. */
public final class SseChannel implements EventChannel {

    private final SseEmitter emitter;

    public SseChannel(SseEmitter emitter) {
        this.emitter = emitter;
    }

    /** 연결이 끝나거나 시간 초과, 오류가 나면 구독을 제거한다 (NFR4.4). */
    public void bind(Subscription subscription) {
        emitter.onCompletion(() -> subscription.detach("COMPLETED"));
        emitter.onTimeout(() -> subscription.detach("TIMEOUT"));
        emitter.onError(error -> subscription.detach("ERROR"));
    }

    @Override
    public void sendSnapshot(Snapshot snapshot) throws IOException {
        emitter.send(SseEmitter.event().name("snapshot").id(String.valueOf(snapshot.version()))
                .data(snapshot, MediaType.APPLICATION_JSON));
    }

    @Override
    public void sendKeepAlive() throws IOException {
        emitter.send(SseEmitter.event().comment(" keep-alive"));
    }

    /** 서버가 끊을 때는 오류로 끝내 브라우저가 같은 URL로 다시 연결하게 한다. */
    @Override
    public void close(String cause) {
        emitter.completeWithError(new IOException("closed by server: " + cause));
    }
}

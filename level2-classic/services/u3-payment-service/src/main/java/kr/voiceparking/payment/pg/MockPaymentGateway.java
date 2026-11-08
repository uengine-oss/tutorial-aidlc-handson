package kr.voiceparking.payment.pg;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.voiceparking.payment.payment.MockScenario;
import kr.voiceparking.payment.payment.Outcome;
import kr.voiceparking.payment.payment.PaymentGateway;
import kr.voiceparking.payment.payment.PgPayment;
import kr.voiceparking.payment.signature.HmacSigner;

/**
 * 같은 프로세스 안의 모의 PG (BR2.1, BR2.2). 걷어내고 실제 PG 구현으로 바꿀 수 있도록 {@link PaymentGateway} 하나 뒤에
 * 있고, 다른 패키지는 이 패키지를 참조하지 않는다(설정만 예외).
 *
 * <p>APPROVE는 바로 APPROVED, FAIL은 바로 FAILED, DELAY는 지정한 초 뒤(0이면 200초) APPROVED로 서명한 웹훅을 자기
 * 웹훅 주소에 보낸다. 시각은 주입한 시계에서 읽고, 지연은 주입한 스케줄러가 맡는다. 스케줄러 스레드는 시각이 되면
 * 발송 실행기에 넘기기만 해서 느린 호출이 다른 알림의 시각을 밀지 않는다. 한 결제에 알림은 한 번이며 실패해도
 * 다시 보내지 않는다.
 */
public class MockPaymentGateway implements PaymentGateway {

    /** DELAY인데 지연이 0이면 쓰는 값: 180초 자동 취소를 재현하려는 시나리오라 그보다 길다. */
    public static final int DEFAULT_DELAY_SECONDS = 200;

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Clock clock;
    private final TaskScheduler scheduler;
    private final Executor dispatch;
    private final WebhookCaller caller;
    private final HmacSigner signer;
    /** PgRequest: 한 결제에 모의 PG 요청은 한 번만 한다. */
    private final Set<String> requested = ConcurrentHashMap.newKeySet();

    public MockPaymentGateway(Clock clock, TaskScheduler scheduler, Executor dispatch, WebhookCaller caller,
            HmacSigner signer) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.dispatch = dispatch;
        this.caller = caller;
        this.signer = signer;
    }

    @Override
    public void register(PgPayment payment) {
        if (!requested.add(payment.paymentId())) {
            return;
        }
        Outcome outcome = payment.mockScenario() == MockScenario.FAIL ? Outcome.FAILED : Outcome.APPROVED;
        Instant at = clock.instant();
        if (payment.mockScenario() == MockScenario.DELAY) {
            int seconds = payment.mockDelaySeconds() == 0 ? DEFAULT_DELAY_SECONDS : payment.mockDelaySeconds();
            at = at.plusSeconds(seconds);
        }
        String paymentId = payment.paymentId();
        scheduler.schedule(() -> handOff(paymentId, outcome), at);
    }

    /** 스케줄러 스레드 안에서 실행된다. 발송 실행기가 거부하면 그 알림만 포기하고 로그를 남긴다. */
    private void handOff(String paymentId, Outcome outcome) {
        try {
            dispatch.execute(() -> deliver(paymentId, outcome));
        } catch (RejectedExecutionException e) {
            log.error("event=pg_callback cause=PG_CALLBACK_DROPPED payment={}", paymentId);
        }
    }

    private void deliver(String paymentId, Outcome outcome) {
        try {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("eventId", UUID.randomUUID().toString());
            call.put("paymentId", paymentId);
            call.put("outcome", outcome.name());
            call.put("occurredAt", clock.instant().toString());
            byte[] body = JSON.writeValueAsString(call).getBytes(StandardCharsets.UTF_8);
            caller.post(body, signer.sign(body));
        } catch (WebhookCaller.CallException e) {
            String cause = e.failure() == WebhookCaller.Cause.NO_RESPONSE ? "PG_CALLBACK_NO_RESPONSE"
                    : "PG_CALLBACK_FAILED";
            log.error("event=pg_callback cause={} payment={} detail={}", cause, paymentId, e.getMessage());
        } catch (Exception e) {
            log.error("event=pg_callback cause=PG_CALLBACK_FAILED payment={} detail={}", paymentId,
                    e.getClass().getSimpleName());
        }
    }
}

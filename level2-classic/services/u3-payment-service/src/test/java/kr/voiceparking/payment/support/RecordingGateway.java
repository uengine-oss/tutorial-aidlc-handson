package kr.voiceparking.payment.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kr.voiceparking.payment.payment.PaymentGateway;
import kr.voiceparking.payment.payment.PgPayment;

/** PG에 등록된 요청을 기록만 하는 가짜 PaymentGateway. failure를 두면 등록 때 그 예외를 던진다. */
public final class RecordingGateway implements PaymentGateway {

    private final List<PgPayment> registered = Collections.synchronizedList(new ArrayList<>());
    public volatile RuntimeException failure;

    @Override
    public void register(PgPayment payment) {
        RuntimeException toThrow = failure;
        if (toThrow != null) {
            throw toThrow;
        }
        registered.add(payment);
    }

    public void reset() {
        registered.clear();
        failure = null;
    }

    public List<PgPayment> registered() {
        synchronized (registered) {
            return new ArrayList<>(registered);
        }
    }

    public int count() {
        return registered.size();
    }
}

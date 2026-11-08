package kr.voiceparking.reservation.support;

import kr.voiceparking.reservation.reservation.PassInfo;
import kr.voiceparking.reservation.reservation.SearchClient;

/** 웹 계층 시험에서 u1 대신 쓰는 가짜. 시험마다 동작을 바꾸고 {@link #reset()}으로 되돌린다. */
public class FakeSearchClient implements SearchClient {

    private static final SearchClient DEFAULT = (passId, hours) -> new PassInfo(passId, "강남 주차장", hours, 3000 * hours);

    private volatile SearchClient delegate = DEFAULT;

    public void behave(SearchClient behavior) {
        this.delegate = behavior;
    }

    public void reset() {
        this.delegate = DEFAULT;
    }

    @Override
    public PassInfo lookup(String passId, int durationHours) {
        return delegate.lookup(passId, durationHours);
    }
}

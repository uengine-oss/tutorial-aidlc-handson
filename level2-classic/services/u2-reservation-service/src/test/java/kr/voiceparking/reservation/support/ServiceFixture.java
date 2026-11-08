package kr.voiceparking.reservation.support;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import kr.voiceparking.reservation.config.ReservationProperties;
import kr.voiceparking.reservation.reservation.DeadlineTimers;
import kr.voiceparking.reservation.reservation.PassInfo;
import kr.voiceparking.reservation.reservation.ReservationService;
import kr.voiceparking.reservation.reservation.ReservationStore;
import kr.voiceparking.reservation.reservation.SearchClient;

/** Reservation 시험용 조립: 주입한 시계, 가짜 u1, 기록용 수신자, 수동 스케줄러. */
public final class ServiceFixture {

    public static final Instant T0 = Instant.parse("2026-11-07T00:00:00Z");
    public static final String SESSION = "11111111-1111-4111-8111-111111111111";
    public static final String OTHER_SESSION = "22222222-2222-4222-8222-222222222222";

    public final TestClock clock = new TestClock(T0);
    public final ReservationStore store = new ReservationStore();
    public final RecordingSink sink = new RecordingSink();
    public final ManualScheduler scheduler = new ManualScheduler();
    public final DeadlineTimers timers = new DeadlineTimers(scheduler.scheduler());
    public final AtomicInteger searchCalls = new AtomicInteger();
    public volatile SearchClient search =
            (passId, hours) -> new PassInfo(passId, "강남 주차장", hours, 3000 * hours);
    public final ReservationService service = new ReservationService(store, this::lookup, sink, timers, clock,
            ReservationProperties.defaults());

    private PassInfo lookup(String passId, int durationHours) {
        searchCalls.incrementAndGet();
        return search.lookup(passId, durationHours);
    }

    public Duration timeout() {
        return Duration.ofSeconds(180);
    }
}

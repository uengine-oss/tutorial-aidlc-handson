package kr.voiceparking.reservation.notification;

/**
 * Reservation이 상태가 바뀔 때마다 스냅샷을 밀어 넣는 한 메서드짜리 인터페이스.
 * 구현은 입출력을 하지 않고 호출 스레드를 막지 않는다 (예약 단위 잠금 안에서 불린다).
 *
 * @param receivedNanos 결제 결과 반영 요청을 받은 {@link System#nanoTime()}. 푸시 지연 로그에 쓴다
 */
public interface SnapshotSink {

    void publish(Snapshot snapshot, long receivedNanos);
}

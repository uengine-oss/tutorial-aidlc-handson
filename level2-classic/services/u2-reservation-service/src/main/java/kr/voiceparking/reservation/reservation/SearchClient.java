package kr.voiceparking.reservation.reservation;

/** u1-search-service 주차권 조회 (C4). 실패는 {@link ReservationException}으로 알린다. */
@FunctionalInterface
public interface SearchClient {

    PassInfo lookup(String passId, int durationHours);
}

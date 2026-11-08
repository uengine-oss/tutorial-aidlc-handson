package kr.voiceparking.reservation.reservation;

/** u1 주차권 조회(C4) 응답. price는 요청 이용 시간 전체의 합계 금액이다. */
public record PassInfo(String passId, String lotName, int durationHours, int price) {
}

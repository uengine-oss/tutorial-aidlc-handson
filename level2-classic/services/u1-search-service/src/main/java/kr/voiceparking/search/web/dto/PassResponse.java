package kr.voiceparking.search.web.dto;

/** C4 응답. price는 durationHours 전체에 대한 합계 금액이다. */
public record PassResponse(String passId, String lotName, int durationHours, int price) {
}

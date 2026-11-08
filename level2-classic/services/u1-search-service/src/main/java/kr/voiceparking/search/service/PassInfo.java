package kr.voiceparking.search.service;

/** 주차권 조회(C4) 결과. price는 요청 이용 시간 전체에 대한 합계 금액이다. */
public record PassInfo(String passId, String lotName, int durationHours, int price) {
}

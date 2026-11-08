package kr.voiceparking.search.domain;

/** 음성 텍스트에서 추출한 검색 조건. 저장하지 않고 요청 처리 중에만 존재한다. */
public record ParsedQuery(String placeName, int durationHours) {
}

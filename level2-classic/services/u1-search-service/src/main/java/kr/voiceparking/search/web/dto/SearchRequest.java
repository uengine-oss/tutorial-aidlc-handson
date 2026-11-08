package kr.voiceparking.search.web.dto;

/** C1 요청 본문. 검증은 컨트롤러가 서비스 로직보다 먼저 한다. */
public record SearchRequest(String text) {
}

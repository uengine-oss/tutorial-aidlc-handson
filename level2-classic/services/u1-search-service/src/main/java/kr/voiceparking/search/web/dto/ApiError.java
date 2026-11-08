package kr.voiceparking.search.web.dto;

/** 공통 오류 형식 {code, message}. 입력 원문은 담지 않는다. */
public record ApiError(String code, String message) {
}

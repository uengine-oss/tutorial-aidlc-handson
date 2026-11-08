package kr.voiceparking.payment.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 공통 오류 형식 {code, message}. 409 PENDING_EXISTS는 기존 reservationId를 더한다. 입력 원문은 담지 않는다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String reservationId) {

    public ApiError(String code, String message) {
        this(code, message, null);
    }
}

package kr.voiceparking.search.service;

/** 예상된 입력·조회 오류. 전역 예외 처리기가 {@link ErrorCode}의 상태와 문구로 바꾼다. */
public class SearchException extends RuntimeException {

    private final ErrorCode errorCode;

    public SearchException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}

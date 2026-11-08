package kr.voiceparking.search.service;

/** 이 서비스가 돌려주는 오류 코드와 HTTP 상태, 고정 안내 문구. 입력 원문은 문구에 넣지 않는다 (NFR6.3). */
public enum ErrorCode {
    MISSING_SESSION(400, "X-Session-Id 헤더(UUID)가 필요합니다"),
    INVALID_TEXT(400, "text는 1자 이상 200자 이하여야 합니다"),
    INVALID_DURATION(400, "durationHours는 1 이상 24 이하의 정수여야 합니다"),
    MISSING_PLACE(422, "장소를 알려 주세요"),
    MISSING_DURATION(422, "이용 시간을 알려 주세요"),
    DURATION_NOT_AVAILABLE(422, "이 주차권은 요청한 이용 시간을 제공하지 못합니다"),
    PASS_NOT_FOUND(404, "주차권을 찾을 수 없습니다"),
    INTERNAL_ERROR(500, "서버 오류가 발생했습니다");

    private final int httpStatus;
    private final String message;

    ErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}

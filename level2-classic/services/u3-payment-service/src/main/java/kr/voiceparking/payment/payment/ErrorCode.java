package kr.voiceparking.payment.payment;

/** 이 서비스가 돌려주는 오류 코드와 HTTP 상태, 고정 안내 문구. 입력 원문은 문구에 넣지 않는다 (NFR6.8). */
public enum ErrorCode {
    INVALID_REQUEST(400, "요청 형식이 올바르지 않습니다"),
    MISSING_SESSION(400, "X-Session-Id 헤더(UUID)가 필요합니다"),
    INVALID_SIGNATURE(401, "서명이 올바르지 않습니다"),
    PASS_NOT_FOUND(404, "주차권을 찾을 수 없습니다"),
    UNKNOWN_PAYMENT(404, "알 수 없는 결제입니다"),
    PENDING_EXISTS(409, "결제 대기 중인 예약이 이미 있습니다"),
    DURATION_NOT_AVAILABLE(422, "이 주차권은 요청한 이용 시간을 제공하지 못합니다"),
    INTERNAL_ERROR(500, "서버 오류가 발생했습니다"),
    UPSTREAM_FAILED(502, "예약 서비스 호출에 실패했습니다");

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

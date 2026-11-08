package kr.voiceparking.reservation.reservation;

/** 이 서비스가 돌려주는 오류 코드와 HTTP 상태, 고정 안내 문구. 입력 원문은 문구에 넣지 않는다 (NFR6.6). */
public enum ErrorCode {
    INVALID_REQUEST(400, "요청 형식이 올바르지 않습니다"),
    MISSING_SESSION(401, "X-Session-Id 헤더(UUID)가 필요합니다"),
    NOT_FOUND(404, "예약을 찾을 수 없습니다"),
    NO_RESERVATION(404, "이 세션의 예약이 없습니다"),
    PASS_NOT_FOUND(404, "주차권을 찾을 수 없습니다"),
    PENDING_EXISTS(409, "결제 대기 중인 예약이 이미 있습니다"),
    DURATION_NOT_AVAILABLE(422, "이 주차권은 요청한 이용 시간을 제공하지 못합니다"),
    INTERNAL_ERROR(500, "서버 오류가 발생했습니다"),
    UPSTREAM_FAILED(502, "주차권 조회에 실패했습니다"),
    SUBSCRIPTION_LIMIT(503, "구독 연결이 가득 찼습니다. 잠시 뒤 다시 시도해 주세요");

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

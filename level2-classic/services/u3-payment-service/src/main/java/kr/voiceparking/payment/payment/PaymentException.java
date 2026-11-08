package kr.voiceparking.payment.payment;

/** 오류 코드를 실어 나르는 예외. PENDING_EXISTS는 u2가 알려 준 기존 예약 ID를 함께 실어 409 본문에 담는다 (BR1.4). */
public class PaymentException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final String reservationId;

    public PaymentException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public PaymentException(ErrorCode errorCode, String reservationId) {
        super(errorCode.name(), null, false, false);
        this.errorCode = errorCode;
        this.reservationId = reservationId;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String reservationId() {
        return reservationId;
    }
}

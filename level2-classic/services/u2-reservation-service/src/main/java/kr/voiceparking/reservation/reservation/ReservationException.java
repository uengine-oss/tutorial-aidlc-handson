package kr.voiceparking.reservation.reservation;

/** 오류 코드를 실어 나르는 예외. PENDING_EXISTS는 기존 예약 ID를 함께 실어 409 본문에 담는다 (BR1.3). */
public class ReservationException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final String reservationId;

    public ReservationException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ReservationException(ErrorCode errorCode, String reservationId) {
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

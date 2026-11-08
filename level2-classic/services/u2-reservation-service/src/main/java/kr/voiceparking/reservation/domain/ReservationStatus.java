package kr.voiceparking.reservation.domain;

/** 예약 상태와 상태별 화면 안내 문구. 결제 대기 외의 세 상태는 종료 상태다 (BR2.5). */
public enum ReservationStatus {
    PENDING_PAYMENT("결제 승인을 기다리는 중입니다"),
    CONFIRMED("예약이 완료되었습니다"),
    PAYMENT_FAILED("결제가 거절되었습니다. 다른 주차권을 선택해 다시 결제해 주세요"),
    CANCELLED("승인 지연으로 예약이 취소되었습니다");

    private final String message;

    ReservationStatus(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}

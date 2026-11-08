package com.voiceparking.payment.client;

/** 예약 서비스에 확정을 요청하는 서비스 간 호출. 단위 테스트에서는 가짜로 바꾼다. */
public interface ReservationClient {

    enum ConfirmResult {
        /** 확정됨(이미 확정이어도 포함) */
        CONFIRMED,
        /** 이미 취소됨(HTTP 409) */
        CANCELLED,
        /** 예약이 없음(HTTP 404) */
        NOT_FOUND
    }

    /**
     * @throws ReservationClientException 예약 서비스에 닿지 못하거나 예상 밖 응답을 받은 경우(재시도 가능)
     */
    ConfirmResult confirm(String reservationId);
}

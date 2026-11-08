package com.voiceparking.reservation.domain;

/** 허용되지 않는 상태 전이(예: 취소된 예약의 확정). HTTP 409로 매핑된다. */
public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(String message) {
        super(message);
    }
}

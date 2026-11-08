package com.voiceparking.payment.client;

public class ReservationClientException extends RuntimeException {
    public ReservationClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReservationClientException(String message) {
        super(message);
    }
}

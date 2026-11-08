package kr.voiceparking.reservation.web.dto;

/** 결제 결과 반영 응답 (C5): 판정과 판정 직후의 예약 상태. */
public record PaymentResultResponse(String result, String reservationStatus) {
}

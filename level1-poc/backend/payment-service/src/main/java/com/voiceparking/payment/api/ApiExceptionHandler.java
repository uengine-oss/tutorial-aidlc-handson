package com.voiceparking.payment.api;

import com.voiceparking.payment.client.ReservationClientException;
import com.voiceparking.payment.service.PaymentConflictException;
import com.voiceparking.payment.service.PaymentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 결제 오류 응답 규칙: 결제 서비스의 모든 결제 실패·오류 응답은
 * HTTP 402 Payment Required + {@code {"code": "...", "message": "..."}} 이다.
 * 앞으로 추가하는 결제 관련 예외도 이 처리기에서 같은 형식으로 매핑한다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public static final String PAYMENT_VALIDATION_FAILED = "PAYMENT_VALIDATION_FAILED";
    public static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
    public static final String PAYMENT_ALREADY_APPROVED = "PAYMENT_ALREADY_APPROVED";
    public static final String RESERVATION_UNAVAILABLE = "RESERVATION_UNAVAILABLE";
    public static final String PAYMENT_INTERNAL_ERROR = "PAYMENT_INTERNAL_ERROR";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 표준 결제 오류 객체. */
    public record ErrorBody(String code, String message) {
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public ErrorBody notFound(PaymentNotFoundException e) {
        return new ErrorBody(PAYMENT_NOT_FOUND, "결제 정보를 찾을 수 없습니다.");
    }

    @ExceptionHandler(PaymentConflictException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public ErrorBody conflict(PaymentConflictException e) {
        return new ErrorBody(PAYMENT_ALREADY_APPROVED, e.getMessage());
    }

    /** 예약 서비스에 닿지 못함: 결제는 PENDING으로 남고 PG가 웹훅을 재시도해야 한다. */
    @ExceptionHandler(ReservationClientException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public ErrorBody upstream(ReservationClientException e) {
        log.error("예약 서비스 호출 실패", e);
        return new ErrorBody(RESERVATION_UNAVAILABLE,
                "예약 서비스 호출에 실패했습니다. 잠시 뒤 다시 시도해 주세요.");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public ErrorBody invalid(Exception e) {
        if (e instanceof MethodArgumentNotValidException m) {
            String message = m.getBindingResult().getFieldErrors().stream()
                    .findFirst().map(f -> f.getDefaultMessage()).orElse("잘못된 요청입니다");
            return new ErrorBody(PAYMENT_VALIDATION_FAILED, message);
        }
        return new ErrorBody(PAYMENT_VALIDATION_FAILED, "요청 본문을 읽을 수 없습니다");
    }

    /** 그 밖의 모든 예외: 내부 정보는 로그에만 남기고 일반 메시지를 돌려준다. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public ErrorBody unexpected(Exception e) {
        log.error("결제 처리 중 예기치 못한 오류", e);
        return new ErrorBody(PAYMENT_INTERNAL_ERROR, "결제 처리 중 오류가 발생했습니다. 잠시 뒤 다시 시도해 주세요.");
    }
}

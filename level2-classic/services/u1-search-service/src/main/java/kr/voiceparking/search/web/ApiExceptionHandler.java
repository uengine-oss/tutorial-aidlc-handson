package kr.voiceparking.search.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import kr.voiceparking.search.service.ErrorCode;
import kr.voiceparking.search.service.SearchException;
import kr.voiceparking.search.web.dto.ApiError;

/**
 * 모든 오류를 공통 형식 {code, message}로 바꾼다. 예외를 삼키지 않고 코드만 로그에 남긴다.
 * 본문에는 고정 문구와 코드만 넣고 입력 원문이나 내부 정보는 넣지 않는다 (NFR2.2, NFR6.3).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String GENERIC_MESSAGE = "요청을 처리할 수 없습니다";

    @ExceptionHandler(SearchException.class)
    ResponseEntity<Object> handleSearch(SearchException ex, WebRequest request) {
        ErrorCode code = ex.errorCode();
        return respond(HttpStatusCode.valueOf(code.httpStatus()), code.name(), code.message(), null, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("unhandled error type={}", ex.getClass().getSimpleName(), ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return respond(HttpStatusCode.valueOf(code.httpStatus()), code.name(), code.message(), null, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorCode code = ErrorCode.INVALID_TEXT;
        return respond(HttpStatusCode.valueOf(code.httpStatus()), code.name(), code.message(), headers, request);
    }

    /** 프레임워크가 만든 나머지 오류(405, 415, 404 등)도 같은 형식으로 낸다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        String code = resolved != null ? resolved.name() : "ERROR";
        return respond(statusCode, code, GENERIC_MESSAGE, headers, request);
    }

    private ResponseEntity<Object> respond(HttpStatusCode status, String code, String message,
            HttpHeaders headers, WebRequest request) {
        request.setAttribute(RequestLogFilter.ERROR_CODE_ATTRIBUTE, code, RequestAttributes.SCOPE_REQUEST);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (headers != null) {
            builder.headers(headers);
        }
        return builder.body(new ApiError(code, message));
    }
}

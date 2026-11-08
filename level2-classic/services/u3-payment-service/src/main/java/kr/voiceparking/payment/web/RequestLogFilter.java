package kr.voiceparking.payment.web;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 한 줄 key=value 로그를 남긴다: 메서드, 경로(쿼리 제외), 상태 코드, 처리 시간(ms), 세션 ID 앞 8자(웹훅 요청은
 * 세션이 없으므로 {@code -}), 4xx와 5xx는 오류 코드. 요청 본문, 서명 값, 쿼리 문자열, 전체 세션 ID는 남기지 않는다
 * (NFR1.8, NFR1.9, NFR6.8). 가장 바깥 필터라 웹훅 본문 필터와 컨트롤러가 걸린 시간도 모두 잰다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLogFilter extends OncePerRequestFilter {

    /** {@link ApiExceptionHandler}가 오류 응답을 만들 때 오류 코드를 실어 두는 요청 속성. */
    public static final String ERROR_CODE_ATTRIBUTE = RequestLogFilter.class.getName() + ".errorCode";
    /** 요청을 받은 {@link System#nanoTime()} 값. 결제 요청의 1.5초 마감이 여기서부터 잰다. */
    public static final String RECEIVED_ATTRIBUTE = RequestLogFilter.class.getName() + ".receivedNanos";

    static final String WEBHOOK_PATH = "/api/payments/webhook";

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        request.setAttribute(RECEIVED_ATTRIBUTE, start);
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            Object errorCode = request.getAttribute(ERROR_CODE_ATTRIBUTE);
            log.info("event=request method={} path={} status={} ms={} session={}{}", safe(request.getMethod()),
                    safe(request.getRequestURI()), response.getStatus(), elapsedMs, shortSession(request),
                    errorCode == null ? "" : " code=" + errorCode);
        }
    }

    private static String shortSession(HttpServletRequest request) {
        if (WEBHOOK_PATH.equals(request.getRequestURI())) {
            return "-";
        }
        String header = request.getHeader("X-Session-Id");
        return SessionIds.isValid(header) ? header.substring(0, 8) : "-";
    }

    /** 제어 문자를 지운다. */
    private static String safe(String value) {
        return value == null ? "-" : value.replaceAll("[\\p{Cntrl}]", "_");
    }
}

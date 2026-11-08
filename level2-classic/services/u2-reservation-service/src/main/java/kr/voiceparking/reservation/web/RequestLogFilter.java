package kr.voiceparking.reservation.web;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 한 줄 key=value 로그를 남긴다: 메서드, 경로(쿼리 제외), 상태 코드, 처리 시간(ms), 세션 ID 앞 8자,
 * 4xx와 5xx는 오류 코드. 요청 본문과 쿼리 문자열, 전체 세션 ID는 남기지 않는다 (NFR1.6, NFR1.7, NFR6.6).
 * 구독 요청은 연결이 열릴 때 이 한 줄이 남고, 구독 시작과 종료는 알림이 따로 남긴다.
 */
@Component
public class RequestLogFilter extends OncePerRequestFilter {

    /** {@link ApiExceptionHandler}가 오류 응답을 만들 때 오류 코드를 실어 두는 요청 속성. */
    public static final String ERROR_CODE_ATTRIBUTE = RequestLogFilter.class.getName() + ".errorCode";

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
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

    /** 헤더(없으면 쿼리의 sessionId)가 UUID 형식일 때만 앞 8자를 남긴다. 그 밖의 값은 로그 위조를 막으려고 버린다. */
    private static String shortSession(HttpServletRequest request) {
        String header = request.getHeader("X-Session-Id");
        String value = header != null ? header : request.getParameter("sessionId");
        return SessionIds.isValid(value) ? value.substring(0, 8) : "-";
    }

    /** 제어 문자를 지운다. */
    private static String safe(String value) {
        return value == null ? "-" : value.replaceAll("[\\p{Cntrl}]", "_");
    }
}

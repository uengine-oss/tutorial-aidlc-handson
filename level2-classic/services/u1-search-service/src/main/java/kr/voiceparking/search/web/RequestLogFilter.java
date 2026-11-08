package kr.voiceparking.search.web;

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
 * 요청마다 한 줄 key=value 로그를 남긴다: 경로, 상태 코드, 처리 시간(ms), 세션 ID 앞 8자, 오류 코드.
 * 요청 본문(입력 문장)과 전체 세션 ID는 남기지 않는다 (NFR3.5, NFR3.6, NFR6.3).
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
            log.info("request path={} status={} durationMs={} session={}{}",
                    safePath(request.getRequestURI()), response.getStatus(), elapsedMs,
                    shortSession(request.getHeader("X-Session-Id")),
                    errorCode == null ? "" : " errorCode=" + errorCode);
        }
    }

    /** UUID 형식일 때만 앞 8자를 남긴다. 그 밖의 값은 로그 위조를 막으려고 버린다. */
    private static String shortSession(String header) {
        if (header != null && SearchController.UUID_FORMAT.matcher(header).matches()) {
            return header.substring(0, 8);
        }
        return "-";
    }

    /** 경로의 제어 문자를 지운다. */
    private static String safePath(String path) {
        return path == null ? "-" : path.replaceAll("[\\p{Cntrl}]", "_");
    }
}

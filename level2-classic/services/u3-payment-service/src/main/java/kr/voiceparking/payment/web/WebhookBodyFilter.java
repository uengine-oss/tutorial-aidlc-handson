package kr.voiceparking.payment.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 웹훅 경로 전용으로 요청 본문의 원문 바이트를 직접, 최대 8193바이트까지만 읽어 요청 속성에 담는다 (NFR6.5). 서명은 해석
 * 전의 원문에 대해 계산하므로 컨트롤러의 {@code @RequestBody}로 읽지 않으며, 그렇게 하면 전체가 먼저 읽힌다. 8192바이트를
 * 넘으면(길이 헤더가 크거나 8193번째 바이트가 있으면) 더 읽지 않고 표시만 하며, 400은 컨트롤러가 낸다. 요청 로그 필터보다
 * 안쪽에서 실행된다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class WebhookBodyFilter extends OncePerRequestFilter {

    public static final String BODY_ATTRIBUTE = WebhookBodyFilter.class.getName() + ".body";
    public static final String OVERSIZE_ATTRIBUTE = WebhookBodyFilter.class.getName() + ".oversize";
    public static final int MAX_BYTES = 8192;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !RequestLogFilter.WEBHOOK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BYTES) {
            request.setAttribute(OVERSIZE_ATTRIBUTE, Boolean.TRUE);
        } else {
            byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
            if (body.length > MAX_BYTES) {
                request.setAttribute(OVERSIZE_ATTRIBUTE, Boolean.TRUE);
            } else {
                request.setAttribute(BODY_ATTRIBUTE, body);
            }
        }
        chain.doFilter(request, response);
    }
}

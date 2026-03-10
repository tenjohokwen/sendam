package com.softropic.sendam.security.infrastructure.filter;

import com.softropic.sendam.common.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that writes the X-Request-ID header to every HTTP response (AUTH-03).
 * <p>
 * Not annotated with {@code @Component} — registered via {@code FilterRegistrationBean} in
 * {@code SecurityConfiguration} so it applies globally to all requests regardless of
 * which security chain handles them.
 * <p>
 * The requestId is placed in MDC by {@code SecurityAdviceFilter.initRequestMetadata} earlier
 * in the filter chain. The {@code !response.containsHeader} guard prevents double-writing if
 * another filter has already added the header.
 */
public class RequestIdResponseFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        chain.doFilter(request, response);
        // MDC is populated by SecurityAdviceFilter.initRequestMetadata earlier in the chain
        String reqId = MDC.get(Constants.REQUEST_ID_NAME);
        if (reqId != null && !response.containsHeader("X-Request-ID")) {
            response.addHeader("X-Request-ID", reqId);
        }
    }
}

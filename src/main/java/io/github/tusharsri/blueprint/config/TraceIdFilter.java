package io.github.tusharsri.blueprint.config;

import io.github.tusharsri.blueprint.util.ApiConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Puts a trace id into the log context (MDC) and the response header; error bodies include it too. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String ATTRIBUTE = TraceIdFilter.class.getName() + ".TRACE_ID";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false; // async requests (timeouts) get re-dispatched; keep the same trace id there
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = (String) request.getAttribute(ATTRIBUTE);
        if (traceId == null) {
            String incoming = request.getHeader(ApiConstants.TRACE_ID_HEADER);
            traceId = (incoming != null && incoming.matches("[A-Za-z0-9-]{1,64}")) ? incoming : UUID.randomUUID().toString();
            request.setAttribute(ATTRIBUTE, traceId);
        }
        MDC.put(ApiConstants.MDC_TRACE_ID, traceId);
        response.setHeader(ApiConstants.TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(ApiConstants.MDC_TRACE_ID);
        }
    }
}

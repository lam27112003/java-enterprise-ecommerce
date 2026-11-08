package com.ecommerce.platform.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Propagates a stable request correlation id (U3/U5). Prefer active Micrometer/W3C traceId when
 * tracing is enabled; otherwise validate/reuse or generate {@code X-Correlation-Id}.
 * Invalid incoming correlation headers are ignored (no HTTP 400). MDC is always cleared.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final int MAX_LENGTH = 128;
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._\\-]{1,128}$");

    private final ObjectProvider<Tracer> tracerProvider;
    private final boolean tracingEnabled;

    public CorrelationIdFilter(
            ObjectProvider<Tracer> tracerProvider,
            @Value("${management.tracing.enabled:false}") boolean tracingEnabled) {
        this.tracerProvider = tracerProvider;
        this.tracingEnabled = tracingEnabled;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String correlationId = resolveCorrelationId(request);
            MDC.put(MDC_KEY, correlationId);
            response.setHeader(HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        if (tracingEnabled) {
            Tracer tracer = tracerProvider.getIfAvailable();
            if (tracer != null) {
                Span current = tracer.currentSpan();
                if (current != null) {
                    String traceId = current.context().traceId();
                    if (traceId != null && !traceId.isBlank()) {
                        return traceId;
                    }
                }
            }
        }
        String incoming = request.getHeader(HEADER);
        if (isValidCorrelationId(incoming)) {
            return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }

    static boolean isValidCorrelationId(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_LENGTH && SAFE_ID.matcher(trimmed).matches();
    }
}

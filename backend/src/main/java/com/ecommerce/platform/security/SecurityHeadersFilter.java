package com.ecommerce.platform.security;

import com.ecommerce.platform.common.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Additive security headers. CSP is report-only (Phase 8). HSTS only when the request is HTTPS.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    /**
     * Report-only policy drafted for SPA + Stripe Payment Element compatibility checks.
     * Not enforced until PHASE8_SECURITY_REVIEW promotion criterion PASS + approval.
     */
    static final String CSP_REPORT_ONLY = String.join("; ",
            "default-src 'self'",
            "base-uri 'self'",
            "object-src 'none'",
            "script-src 'self' https://js.stripe.com",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: https:",
            "font-src 'self' data:",
            "connect-src 'self' https://api.stripe.com https://hooks.stripe.com",
            "frame-src https://js.stripe.com https://hooks.stripe.com",
            "form-action 'self'");

    private final ClientIpResolver clientIpResolver;

    public SecurityHeadersFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Content-Security-Policy-Report-Only", CSP_REPORT_ONLY);
        if (isHttps(request)) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        filterChain.doFilter(request, response);
    }

    private boolean isHttps(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        // Honor X-Forwarded-Proto only when the peer is a trusted proxy (same rules as ClientIp).
        String remote = request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
        if (!clientIpResolver.isTrustedProxy(remote)) {
            return false;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && "https".equalsIgnoreCase(proto.trim());
    }
}

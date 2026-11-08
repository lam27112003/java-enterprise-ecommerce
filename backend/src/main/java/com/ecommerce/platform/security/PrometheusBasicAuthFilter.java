package com.ecommerce.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * HTTP Basic authentication for {@code /actuator/prometheus} scrape (U1 / P8-D10).
 * Does not open the endpoint publicly; credentials come from environment variables.
 */
@Component
public class PrometheusBasicAuthFilter extends OncePerRequestFilter {

    public static final String ROLE_ACTUATOR = "ROLE_ACTUATOR";

    private final boolean prometheusPublic;
    private final String username;
    private final String password;

    public PrometheusBasicAuthFilter(
            @Value("${app.security.actuator-prometheus-public:false}") boolean prometheusPublic,
            @Value("${app.security.actuator-prometheus-username:}") String username,
            @Value("${app.security.actuator-prometheus-password:}") String password) {
        this.prometheusPublic = prometheusPublic;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (prometheusPublic) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.equals("/actuator/prometheus");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (username.isBlank() || password.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"prometheus\"");
            return;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"prometheus\"");
            return;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"prometheus\"");
            return;
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"prometheus\"");
            return;
        }
        String user = decoded.substring(0, colon);
        String pass = decoded.substring(colon + 1);
        if (!username.equals(user) || !password.equals(pass)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"prometheus\"");
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority(ROLE_ACTUATOR)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    /** Prevent double registration as a servlet filter (must run only inside SecurityFilterChain). */
    @Bean
    FilterRegistrationBean<PrometheusBasicAuthFilter> prometheusBasicAuthFilterRegistration(
            PrometheusBasicAuthFilter filter) {
        FilterRegistrationBean<PrometheusBasicAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

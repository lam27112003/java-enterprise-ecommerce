package com.ecommerce.platform.security;

import com.ecommerce.platform.auth.exception.InvalidTokenException;
import com.ecommerce.platform.common.api.ErrorResponse;
import com.ecommerce.platform.common.exception.BusinessException;
import com.ecommerce.platform.security.token.AccessTokenDenylist;
import com.ecommerce.platform.security.token.DenylistUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AccessTokenDenylist accessTokenDenylist;
    private final DatabaseUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AccessTokenDenylist accessTokenDenylist,
            DatabaseUserDetailsService userDetailsService,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.accessTokenDenylist = accessTokenDenylist;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                ParsedAccessToken parsed = jwtService.parseAndValidate(token);
                if (accessTokenDenylist.isRevoked(parsed.jti())) {
                    throw new InvalidTokenException("Access token has been revoked");
                }
                AuthenticatedUser principal = userDetailsService.loadAuthorizedUser(parsed.userId());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        } catch (DenylistUnavailableException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, request, ex);
        } catch (BusinessException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, request, ex);
        }
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request, BusinessException ex)
            throws IOException {
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(ex.getCode(), ex.getMessage(), request.getRequestURI()));
    }
}

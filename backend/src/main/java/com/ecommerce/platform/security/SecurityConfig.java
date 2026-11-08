package com.ecommerce.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PrometheusBasicAuthFilter prometheusBasicAuthFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final boolean apiDocsPublic;
    private final boolean actuatorPrometheusPublic;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            PrometheusBasicAuthFilter prometheusBasicAuthFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            @Value("${app.security.api-docs-public:false}") boolean apiDocsPublic,
            @Value("${app.security.actuator-prometheus-public:false}") boolean actuatorPrometheusPublic) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.prometheusBasicAuthFilter = prometheusBasicAuthFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.apiDocsPublic = apiDocsPublic;
        this.actuatorPrometheusPublic = actuatorPrometheusPublic;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> publicMatchers = new ArrayList<>();
        publicMatchers.add("/actuator/health");
        publicMatchers.add("/actuator/health/**");
        publicMatchers.add("/api/v1/platform/**");
        publicMatchers.add("/api/v1/auth/register");
        publicMatchers.add("/api/v1/auth/login");
        publicMatchers.add("/api/v1/auth/refresh");
        if (actuatorPrometheusPublic) {
            publicMatchers.add("/actuator/info");
            publicMatchers.add("/actuator/prometheus");
        }
        if (apiDocsPublic) {
            publicMatchers.add("/v3/api-docs/**");
            publicMatchers.add("/swagger-ui/**");
            publicMatchers.add("/swagger-ui.html");
        }

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.disable())
                )
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(publicMatchers.toArray(String[]::new)).permitAll();
                    if (!actuatorPrometheusPublic) {
                        auth.requestMatchers("/actuator/prometheus").hasRole("ACTUATOR");
                    }
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook/stripe").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .requestMatchers("/api/v1/cart", "/api/v1/cart/**").hasRole("CUSTOMER")
                            .requestMatchers("/api/v1/orders", "/api/v1/orders/**").hasRole("CUSTOMER")
                            .requestMatchers("/api/v1/payments", "/api/v1/payments/**").hasAnyRole("CUSTOMER", "ADMIN")
                            .requestMatchers("/api/v1/promotions", "/api/v1/promotions/**").hasRole("CUSTOMER")
                            .requestMatchers("/api/v1/customer/**").hasAnyRole("CUSTOMER", "ADMIN")
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(prometheusBasicAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

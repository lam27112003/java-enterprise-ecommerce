package com.ecommerce.platform.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Serializes admin user status mutations that can remove an ACTIVE ADMIN (P7 §8.1).
 * PostgreSQL: {@code pg_advisory_xact_lock}; H2/other: JVM monitor fallback for unit tests.
 */
@Component
public class AdminUserStatusLock {

    /**
     * Fixed 64-bit advisory lock key documented for last-ADMIN serialization (Phase 7).
     */
    public static final long ADMIN_STATUS_ADVISORY_LOCK_KEY = 0x5048375F41444D4EL; // "P7_ADMN"

    private static final Object JVM_FALLBACK_MONITOR = new Object();

    private final JdbcTemplate jdbcTemplate;
    private final String datasourceUrl;

    public AdminUserStatusLock(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.datasourceUrl = datasourceUrl == null ? "" : datasourceUrl;
    }

    public void runUnderLock(Runnable action) {
        if (isPostgres()) {
            jdbcTemplate.execute(
                    "SELECT pg_advisory_xact_lock(" + ADMIN_STATUS_ADVISORY_LOCK_KEY + ")");
            action.run();
            return;
        }
        synchronized (JVM_FALLBACK_MONITOR) {
            action.run();
        }
    }

    private boolean isPostgres() {
        return datasourceUrl.toLowerCase().contains("postgresql");
    }
}

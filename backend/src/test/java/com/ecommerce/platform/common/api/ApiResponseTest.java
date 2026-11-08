package com.ecommerce.platform.common.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void okWrapsPayload() {
        ApiResponse<Map<String, String>> response = ApiResponse.ok(Map.of("status", "UP"));

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("status", "UP");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void failureHasNoData() {
        ApiResponse<Void> response = ApiResponse.failure("boom");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("boom");
        assertThat(response.data()).isNull();
    }
}

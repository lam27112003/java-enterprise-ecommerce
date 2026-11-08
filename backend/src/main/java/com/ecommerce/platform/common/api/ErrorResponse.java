package com.ecommerce.platform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        String code,
        String message,
        List<FieldErrorDetail> fieldErrors,
        Instant timestamp,
        String path
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(false, code, message, null, Instant.now(), path);
    }

    public static ErrorResponse of(String code, String message, String path, List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(false, code, message, fieldErrors, Instant.now(), path);
    }

    public record FieldErrorDetail(String field, String message) {
    }
}

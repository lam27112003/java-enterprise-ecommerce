package com.ecommerce.platform.auth.exception;

import com.ecommerce.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class AuthExceptions {

    private AuthExceptions() {
    }

    public static BusinessException duplicateEmail() {
        return new BusinessException("EMAIL_ALREADY_EXISTS", "Email is already registered", HttpStatus.CONFLICT);
    }

    public static BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException accountDisabled() {
        return new BusinessException("ACCOUNT_DISABLED", "Account is not active", HttpStatus.FORBIDDEN);
    }

    public static BusinessException roleMissing(String role) {
        return new BusinessException("ROLE_NOT_CONFIGURED", "Required role is not configured: " + role, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

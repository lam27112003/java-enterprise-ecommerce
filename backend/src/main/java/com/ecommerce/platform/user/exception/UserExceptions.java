package com.ecommerce.platform.user.exception;

import com.ecommerce.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class UserExceptions {

    private UserExceptions() {
    }

    public static BusinessException notFound() {
        return new BusinessException("USER_NOT_FOUND", "User was not found", HttpStatus.NOT_FOUND);
    }

    public static BusinessException selfStatusChangeForbidden() {
        return new BusinessException(
                "USER_SELF_STATUS_CHANGE_FORBIDDEN",
                "Administrators cannot change their own account status",
                HttpStatus.CONFLICT);
    }

    public static BusinessException lastActiveAdminForbidden() {
        return new BusinessException(
                "LAST_ACTIVE_ADMIN_FORBIDDEN",
                "Cannot disable or suspend the last active administrator",
                HttpStatus.CONFLICT);
    }

    public static BusinessException invalidStatus() {
        return new BusinessException(
                "USER_INVALID_STATUS",
                "User status must be ACTIVE, DISABLED, or SUSPENDED",
                HttpStatus.BAD_REQUEST);
    }
}

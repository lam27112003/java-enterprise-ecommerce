package com.ecommerce.platform.security.token;

import com.ecommerce.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DenylistUnavailableException extends BusinessException {

    public DenylistUnavailableException(String message, Throwable cause) {
        super("AUTH_STORE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
        initCause(cause);
    }
}

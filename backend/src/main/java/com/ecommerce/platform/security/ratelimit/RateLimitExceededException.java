package com.ecommerce.platform.security.ratelimit;

import com.ecommerce.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException() {
        super("RATE_LIMIT_EXCEEDED", "Too many requests. Please try again later.", HttpStatus.TOO_MANY_REQUESTS);
    }
}

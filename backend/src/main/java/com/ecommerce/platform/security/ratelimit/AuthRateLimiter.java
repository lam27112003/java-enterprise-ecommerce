package com.ecommerce.platform.security.ratelimit;

public interface AuthRateLimiter {

    void checkLogin(String clientIp, String email);

    void checkRegister(String clientIp);

    void checkRefresh(String clientIp);
}

package com.ecommerce.platform.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int loginPerMinute = 10;
    private int registerPerMinute = 5;
    private int refreshPerMinute = 30;
    private int windowSeconds = 60;

    private int checkoutPerAccountPerMinute = 30;
    private int checkoutPerIpPerMinute = 60;
    private int payPerAccountPerMinute = 30;
    private int payPerIpPerMinute = 60;
    private int adminRefundConfirmPerAccountPerMinute = 20;
    private int adminRefundConfirmPerIpPerMinute = 40;
    private int adminRefundReopenPerAccountPerMinute = 10;
    private int adminRefundReopenPerIpPerMinute = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLoginPerMinute() {
        return loginPerMinute;
    }

    public void setLoginPerMinute(int loginPerMinute) {
        this.loginPerMinute = loginPerMinute;
    }

    public int getRegisterPerMinute() {
        return registerPerMinute;
    }

    public void setRegisterPerMinute(int registerPerMinute) {
        this.registerPerMinute = registerPerMinute;
    }

    public int getRefreshPerMinute() {
        return refreshPerMinute;
    }

    public void setRefreshPerMinute(int refreshPerMinute) {
        this.refreshPerMinute = refreshPerMinute;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getCheckoutPerAccountPerMinute() {
        return checkoutPerAccountPerMinute;
    }

    public void setCheckoutPerAccountPerMinute(int checkoutPerAccountPerMinute) {
        this.checkoutPerAccountPerMinute = checkoutPerAccountPerMinute;
    }

    public int getCheckoutPerIpPerMinute() {
        return checkoutPerIpPerMinute;
    }

    public void setCheckoutPerIpPerMinute(int checkoutPerIpPerMinute) {
        this.checkoutPerIpPerMinute = checkoutPerIpPerMinute;
    }

    public int getPayPerAccountPerMinute() {
        return payPerAccountPerMinute;
    }

    public void setPayPerAccountPerMinute(int payPerAccountPerMinute) {
        this.payPerAccountPerMinute = payPerAccountPerMinute;
    }

    public int getPayPerIpPerMinute() {
        return payPerIpPerMinute;
    }

    public void setPayPerIpPerMinute(int payPerIpPerMinute) {
        this.payPerIpPerMinute = payPerIpPerMinute;
    }

    public int getAdminRefundConfirmPerAccountPerMinute() {
        return adminRefundConfirmPerAccountPerMinute;
    }

    public void setAdminRefundConfirmPerAccountPerMinute(int adminRefundConfirmPerAccountPerMinute) {
        this.adminRefundConfirmPerAccountPerMinute = adminRefundConfirmPerAccountPerMinute;
    }

    public int getAdminRefundConfirmPerIpPerMinute() {
        return adminRefundConfirmPerIpPerMinute;
    }

    public void setAdminRefundConfirmPerIpPerMinute(int adminRefundConfirmPerIpPerMinute) {
        this.adminRefundConfirmPerIpPerMinute = adminRefundConfirmPerIpPerMinute;
    }

    public int getAdminRefundReopenPerAccountPerMinute() {
        return adminRefundReopenPerAccountPerMinute;
    }

    public void setAdminRefundReopenPerAccountPerMinute(int adminRefundReopenPerAccountPerMinute) {
        this.adminRefundReopenPerAccountPerMinute = adminRefundReopenPerAccountPerMinute;
    }

    public int getAdminRefundReopenPerIpPerMinute() {
        return adminRefundReopenPerIpPerMinute;
    }

    public void setAdminRefundReopenPerIpPerMinute(int adminRefundReopenPerIpPerMinute) {
        this.adminRefundReopenPerIpPerMinute = adminRefundReopenPerIpPerMinute;
    }
}

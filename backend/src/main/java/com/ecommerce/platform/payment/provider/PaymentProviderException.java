package com.ecommerce.platform.payment.provider;

public class PaymentProviderException extends RuntimeException {

    private final boolean uncertain;

    public PaymentProviderException(String message, Throwable cause, boolean uncertain) {
        super(message, cause);
        this.uncertain = uncertain;
    }

    public boolean isUncertain() {
        return uncertain;
    }
}

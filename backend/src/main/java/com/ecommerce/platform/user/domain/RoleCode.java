package com.ecommerce.platform.user.domain;

public enum RoleCode {
    ADMIN,
    CUSTOMER;

    public String authority() {
        return "ROLE_" + name();
    }
}

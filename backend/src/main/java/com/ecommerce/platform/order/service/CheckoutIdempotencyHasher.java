package com.ecommerce.platform.order.service;

import com.ecommerce.platform.order.dto.OrderDtos.CheckoutRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Canonical checkout payload fingerprint (P5-D3).
 *
 * <pre>
 * Algorithm:
 * 1. Normalize promotionCode: trim + uppercase; empty if null/blank.
 * 2. Normalize shippingAddressJson: trim; empty if null/blank.
 * 3. Build UTF-8 string:
 *      v1
 *      promotionCode=&lt;normalized&gt;
 *      shippingAddressJson=&lt;normalized&gt;
 * 4. SHA-256 digest, lowercase hex encoding.
 * </pre>
 *
 * Cart contents are intentionally excluded: cart is server-owned for the authenticated
 * customer; idempotency keys the client request body, and a successful first checkout
 * clears the cart so retries return the original order.
 */
public final class CheckoutIdempotencyHasher {

    private CheckoutIdempotencyHasher() {
    }

    public static String hash(CheckoutRequest request) {
        String promo = normalizeCode(request == null ? null : request.promotionCode());
        String shipping = normalizeShipping(request == null ? null : request.shippingAddressJson());
        String canonical = "v1\npromotionCode=" + promo + "\nshippingAddressJson=" + shipping + "\n";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return code.trim().toUpperCase();
    }

    private static String normalizeShipping(String shipping) {
        if (shipping == null || shipping.isBlank()) {
            return "";
        }
        return shipping.trim();
    }
}

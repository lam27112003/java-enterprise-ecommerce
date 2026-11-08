package com.ecommerce.platform.common.web;

import com.ecommerce.platform.security.TrustedProxyProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Resolves client IP for rate limiting.
 * X-Forwarded-For / X-Real-IP are honored only when the immediate peer is an explicitly trusted proxy.
 */
@Component
public class ClientIpResolver {

    private final TrustedProxyProperties trustedProxyProperties;

    public ClientIpResolver(TrustedProxyProperties trustedProxyProperties) {
        this.trustedProxyProperties = trustedProxyProperties;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String client = forwarded.split(",")[0].trim();
            if (!client.isEmpty()) {
                return client;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return remoteAddr;
    }

    public boolean isTrustedProxy(String remoteAddr) {
        List<String> trusted = trustedProxyProperties.getTrustedProxies();
        if (trusted == null || trusted.isEmpty() || remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        for (String entry : trusted) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String candidate = entry.trim();
            if (candidate.contains("/")) {
                if (matchesCidr(remoteAddr, candidate)) {
                    return true;
                }
            } else if (candidate.equalsIgnoreCase(remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            InetAddress target = InetAddress.getByName(ip);
            InetAddress network = InetAddress.getByName(parts[0]);
            int prefix = Integer.parseInt(parts[1]);
            byte[] targetBytes = target.getAddress();
            byte[] networkBytes = network.getAddress();
            if (targetBytes.length != networkBytes.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            int remBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (targetBytes[i] != networkBytes[i]) {
                    return false;
                }
            }
            if (remBits == 0) {
                return true;
            }
            int mask = (~0) << (8 - remBits);
            return (targetBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
        } catch (UnknownHostException | NumberFormatException | ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }
}

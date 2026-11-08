package com.ecommerce.platform.common.web;

import com.ecommerce.platform.security.TrustedProxyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersFromUntrustedPeers() {
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setTrustedProxies(List.of());
        ClientIpResolver resolver = new ClientIpResolver(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void trustsForwardedHeadersOnlyFromConfiguredProxy() {
        TrustedProxyProperties props = new TrustedProxyProperties();
        props.setTrustedProxies(List.of("10.0.0.0/8", "127.0.0.1"));
        ClientIpResolver resolver = new ClientIpResolver(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "198.51.100.77, 10.1.2.3");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.77");
    }
}

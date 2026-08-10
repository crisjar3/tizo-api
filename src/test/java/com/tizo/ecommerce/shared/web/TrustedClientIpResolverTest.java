package com.tizo.ecommerce.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedClientIpResolverTest {

    @Test
    void acceptsForwardedClientOnlyFromConfiguredAlbNetworks() {
        TrustedClientIpResolver resolver = new TrustedClientIpResolver("10.0.1.0/24,2001:db8::/48");
        MockHttpServletRequest trusted = new MockHttpServletRequest();
        trusted.setRemoteAddr("10.0.1.42");
        trusted.addHeader("X-Forwarded-For", "203.0.113.8, 10.0.1.42");

        MockHttpServletRequest untrusted = new MockHttpServletRequest();
        untrusted.setRemoteAddr("10.0.2.42");
        untrusted.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(resolver.resolve(trusted)).isEqualTo("203.0.113.8");
        assertThat(resolver.resolve(untrusted)).isEqualTo("10.0.2.42");
    }
}

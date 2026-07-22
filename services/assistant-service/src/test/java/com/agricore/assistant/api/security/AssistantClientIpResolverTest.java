package com.agricore.assistant.api.security;

import com.agricore.assistant.infrastructure.configuration.AssistantBudgetProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantClientIpResolverTest {

    private final AssistantBudgetProperties properties = new AssistantBudgetProperties();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void usesRemoteAddressByDefaultAndIgnoresSpoofableForwardedHeader() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.4");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");

        assertThat(new AssistantClientIpResolver(properties).resolve(request)).isEqualTo("10.0.0.4");
    }

    @Test
    void usesFirstForwardedHopOnlyWhenExplicitlyTrusted() {
        properties.setTrustForwardedHeaders(true);
        when(request.getRemoteAddr()).thenReturn("10.0.0.4");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.4");

        assertThat(new AssistantClientIpResolver(properties).resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void rejectsMalformedForwardedAddressEvenWhenForwardingIsTrusted() {
        properties.setTrustForwardedHeaders(true);
        when(request.getRemoteAddr()).thenReturn("10.0.0.4");
        when(request.getHeader("X-Forwarded-For")).thenReturn("spoofed-client");

        assertThat(new AssistantClientIpResolver(properties).resolve(request)).isEqualTo("10.0.0.4");
    }

    @Test
    void canonicalizesForwardedIpv4AddressBeforeBudgetKeying() {
        properties.setTrustForwardedHeaders(true);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.000.113.010");

        assertThat(new AssistantClientIpResolver(properties).resolve(request)).isEqualTo("203.0.113.10");
    }
}

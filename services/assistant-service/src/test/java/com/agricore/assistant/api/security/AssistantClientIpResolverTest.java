package com.agricore.assistant.api.security;

import com.agricore.common.security.ClientIpHeaderSigner;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantClientIpResolverTest {

    private static final String SECRET = "test-client-ip-signing-secret";
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void rawForwardedHeaderCannotSpoofTheBudgetClientIp() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.4");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");

        assertThat(new AssistantClientIpResolver(SECRET).resolve(request)).isEqualTo("10.0.0.4");
    }

    @Test
    void invalidSignedHeaderFallsBackToImmediateRemoteAddress() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.4");
        when(request.getHeader(ClientIpHeaderSigner.CLIENT_IP_HEADER)).thenReturn("203.0.113.10");
        when(request.getHeader(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER))
                .thenReturn(ClientIpHeaderSigner.sign("203.0.113.11", SECRET));

        assertThat(new AssistantClientIpResolver(SECRET).resolve(request)).isEqualTo("10.0.0.4");
    }

    @Test
    void validSignedHeaderResolvesCanonicalClientIp() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.4");
        when(request.getHeader(ClientIpHeaderSigner.CLIENT_IP_HEADER)).thenReturn("203.0.113.10");
        when(request.getHeader(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER))
                .thenReturn(ClientIpHeaderSigner.sign("203.000.113.010", SECRET));

        assertThat(new AssistantClientIpResolver(SECRET).resolve(request)).isEqualTo("203.0.113.10");
    }
}

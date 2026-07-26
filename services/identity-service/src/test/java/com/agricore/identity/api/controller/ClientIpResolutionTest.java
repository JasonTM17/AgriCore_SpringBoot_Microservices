package com.agricore.identity.api.controller;

import com.agricore.identity.api.request.LoginRequest;
import com.agricore.identity.application.service.AuthApplicationService;
import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The rate limiter keys on whatever this resolves, so a caller who can choose the value can mint a
 * fresh bucket per request and defeat the limit entirely.
 *
 * <p>The gateway appends its observed peer address to X-Forwarded-For rather than replacing the
 * header, so only the last entry is trustworthy. These tests pin that direction: a forged prefix
 * must not survive.
 */
class ClientIpResolutionTest {

    private static final String SPOOFED = "1.1.1.1";
    private static final String OBSERVED_BY_GATEWAY = "203.0.113.55";

    private static SecurityProperties props(boolean trustForwardedHeaders) {
        return new SecurityProperties(
                "https://agricore.test/identity",
                "agricore-api",
                300L,
                3600L,
                4,
                5,
                15,
                20,
                "",
                "",
                true,
                false,
                trustForwardedHeaders
        );
    }

    private static String resolvedIpFor(MockHttpServletRequest request, boolean trustForwardedHeaders) {
        AuthApplicationService auth = mock(AuthApplicationService.class);
        AuthController controller = new AuthController(auth, props(trustForwardedHeaders));

        controller.login(new LoginRequest("user@agricore.test", "Secret123!"), request);

        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(auth).login(any(LoginRequest.class), ip.capture(), eq("junit-agent"));
        return ip.getValue();
    }

    private static MockHttpServletRequest requestWith(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "junit-agent");
        request.setRemoteAddr("10.0.0.1");
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void forgedLeadingHopIsIgnored_lastHopWins() {
        String resolved = resolvedIpFor(requestWith(SPOOFED + ", " + OBSERVED_BY_GATEWAY), true);

        assertThat(resolved)
                .as("a client-supplied leading hop must not become the rate-limit key")
                .isEqualTo(OBSERVED_BY_GATEWAY);
    }

    @Test
    void rotatingTheForgedPrefixDoesNotChangeTheKey() {
        String first = resolvedIpFor(requestWith("9.9.9.9, " + OBSERVED_BY_GATEWAY), true);
        String second = resolvedIpFor(requestWith("8.8.8.8, " + OBSERVED_BY_GATEWAY), true);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void singleHopIsUsedAsIs() {
        assertThat(resolvedIpFor(requestWith(OBSERVED_BY_GATEWAY), true)).isEqualTo(OBSERVED_BY_GATEWAY);
    }

    @Test
    void trailingBlankHopsAreSkipped() {
        assertThat(resolvedIpFor(requestWith(SPOOFED + ", " + OBSERVED_BY_GATEWAY + " , "), true))
                .isEqualTo(OBSERVED_BY_GATEWAY);
    }

    @Test
    void headerIsIgnoredEntirelyWhenNotTrusted() {
        assertThat(resolvedIpFor(requestWith(SPOOFED + ", " + OBSERVED_BY_GATEWAY), false)).isEqualTo("10.0.0.1");
    }

    @Test
    void fallsBackToRemoteAddrWhenHeaderAbsent() {
        assertThat(resolvedIpFor(requestWith(null), true)).isEqualTo("10.0.0.1");
    }
}

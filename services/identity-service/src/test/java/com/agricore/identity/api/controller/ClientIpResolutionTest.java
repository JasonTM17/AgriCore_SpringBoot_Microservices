package com.agricore.identity.api.controller;

import com.agricore.identity.api.request.LoginRequest;
import com.agricore.identity.api.response.AuthTokensResponse;
import com.agricore.identity.application.service.AuthApplicationService;
import com.agricore.identity.infrastructure.security.RefreshCookieSupport;
import com.agricore.identity.infrastructure.security.SignedClientIpResolver;
import com.agricore.common.security.ClientIpHeaderSigner;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientIpResolutionTest {

    private static final String SECRET = "test-client-ip-signing-secret";
    private static final String GATEWAY_CLIENT = "203.0.113.55";

    private static String resolvedApiIpFor(MockHttpServletRequest request) {
        AuthApplicationService auth = mock(AuthApplicationService.class);
        AuthController controller = new AuthController(auth, new SignedClientIpResolver(SECRET));

        controller.login(new LoginRequest("user@agricore.test", "Secret123!"), request);

        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(auth).login(any(LoginRequest.class), ip.capture(), eq("junit-agent"));
        return ip.getValue();
    }

    private static String resolvedWebIpFor(MockHttpServletRequest request) {
        AuthApplicationService auth = mock(AuthApplicationService.class);
        RefreshCookieSupport cookies = mock(RefreshCookieSupport.class);
        WebAuthController controller = new WebAuthController(auth, new SignedClientIpResolver(SECRET), cookies);
        AuthTokensResponse tokens = new AuthTokensResponse(
                "access-token",
                "refresh-token",
                "Bearer",
                300,
                null
        );
        when(auth.login(any(LoginRequest.class), any(String.class), eq("junit-agent"))).thenReturn(tokens);
        when(cookies.buildRefreshCookie("refresh-token"))
                .thenReturn(ResponseCookie.from("agricore_refresh", "refresh-token").build());

        controller.login(new LoginRequest("user@agricore.test", "Secret123!"), request);

        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(auth).login(any(LoginRequest.class), ip.capture(), eq("junit-agent"));
        return ip.getValue();
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "junit-agent");
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @Test
    void rawForwardedHeadersCannotMintANewLoginRateLimitBucket() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "1.1.1.1, " + GATEWAY_CLIENT);
        request.addHeader(ClientIpHeaderSigner.CLIENT_IP_HEADER, GATEWAY_CLIENT);
        request.addHeader(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER, "forged");

        assertThat(resolvedApiIpFor(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void invalidSignedHeaderFallsBackToImmediateRemoteAddress() {
        MockHttpServletRequest request = request();
        request.addHeader(ClientIpHeaderSigner.CLIENT_IP_HEADER, GATEWAY_CLIENT);
        request.addHeader(
                ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER,
                ClientIpHeaderSigner.sign("203.0.113.99", SECRET)
        );

        assertThat(resolvedApiIpFor(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void validSignedHeaderResolvesForApiAndWebLogin() {
        MockHttpServletRequest apiRequest = request();
        apiRequest.addHeader(ClientIpHeaderSigner.CLIENT_IP_HEADER, GATEWAY_CLIENT);
        apiRequest.addHeader(
                ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER,
                ClientIpHeaderSigner.sign(GATEWAY_CLIENT, SECRET)
        );
        MockHttpServletRequest webRequest = new MockHttpServletRequest();
        webRequest.setRemoteAddr("10.0.0.1");
        webRequest.addHeader("User-Agent", "junit-agent");
        webRequest.addHeader(ClientIpHeaderSigner.CLIENT_IP_HEADER, GATEWAY_CLIENT);
        webRequest.addHeader(
                ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER,
                ClientIpHeaderSigner.sign(GATEWAY_CLIENT, SECRET)
        );

        assertThat(resolvedApiIpFor(apiRequest)).isEqualTo(GATEWAY_CLIENT);
        assertThat(resolvedWebIpFor(webRequest)).isEqualTo(GATEWAY_CLIENT);
    }
}

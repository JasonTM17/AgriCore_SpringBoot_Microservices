package com.agricore.gateway.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayCsrfProtectionMatcherTest {

    @Test
    void matchesCookieBackedUnsafeDomainRequest() {
        assertThat(matches(MockServerHttpRequest.post("/api/v1/farms")
                .cookie(new HttpCookie("browser-session", "value"))
                .build())).isTrue();
    }

    @Test
    void doesNotMatchUnsafeRequestWithoutCookies() {
        assertThat(matches(MockServerHttpRequest.post("/api/v1/farms").build())).isFalse();
    }

    @Test
    void doesNotMatchSafeCookieBackedRequest() {
        assertThat(matches(MockServerHttpRequest.get("/api/v1/farms")
                .cookie(new HttpCookie("browser-session", "value"))
                .build())).isFalse();
    }

    @Test
    void doesNotMatchCookieBackedWebRefreshRequest() {
        assertThat(matches(MockServerHttpRequest.post("/api/v1/auth/web/refresh")
                .cookie(new HttpCookie("browser-session", "value"))
                .build())).isFalse();
    }

    @Test
    void doesNotMatchCookieBackedAuthRefreshRequest() {
        assertThat(matches(MockServerHttpRequest.post("/api/v1/auth/refresh")
                .cookie(new HttpCookie("browser-session", "value"))
                .build())).isFalse();
    }

    @Test
    void doesNotMatchCookieBackedAuthRootRequest() {
        assertThat(matches(MockServerHttpRequest.post("/api/v1/auth")
                .cookie(new HttpCookie("browser-session", "value"))
                .build())).isFalse();
    }

    @Test
    void matchesSimilarButNonAuthPath() {
        assertThat(matches(MockServerHttpRequest.post("/api/v1/authentication")
                .cookie(new HttpCookie("browser-session", "value"))
                .build())).isTrue();
    }

    @Test
    void doesNotMatchCookieBackedBearerRequest() {
        assertThat(matches(MockServerHttpRequest.post("/api/v1/farms")
                .cookie(new HttpCookie("browser-session", "value"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .build())).isFalse();
    }

    @Test
    void doesNotTreatEmptyBearerSchemeAsExplicitCredentials() {
        assertThat(matches(MockServerHttpRequest.post("/api/v1/farms")
                .cookie(new HttpCookie("browser-session", "value"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                .build())).isTrue();
    }

    @Test
    void doesNotTreatNonBearerAuthorizationAsExplicitCredentials() {
        assertThat(matches(MockServerHttpRequest.post("/api/v1/farms")
                .cookie(new HttpCookie("browser-session", "value"))
                .header(HttpHeaders.AUTHORIZATION, "Basic credentials")
                .build())).isTrue();
    }

    @Test
    void doesNotMatchCookieBackedPreflightRequest() {
        assertThat(matches(MockServerHttpRequest.options("/api/v1/farms")
                .cookie(new HttpCookie("browser-session", "value"))
                .build())).isFalse();
    }

    @Test
    void rejectingRepositoryDoesNotPersistOrExposeCsrfTokens() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/farms").build()
        );
        StatelessRejectingServerCsrfTokenRepository repository =
                new StatelessRejectingServerCsrfTokenRepository();

        CsrfToken generated = repository.generateToken(exchange).block();
        repository.saveToken(exchange, generated).block();

        assertThat(generated).isNotNull();
        assertThat(generated.getHeaderName()).isEqualTo("X-AGRICORE-GATEWAY-XSRF-TOKEN");
        assertThat(repository.loadToken(exchange).blockOptional()).isEmpty();
        assertThat(exchange.getResponse().getCookies()).isEmpty();
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(exchange.getResponse().getHeaders().getFirst(generated.getHeaderName())).isNull();
    }

    private static boolean matches(MockServerHttpRequest request) {
        return GatewaySecurityConfig.requiresCookieConditionedCsrfProtection(
                        MockServerWebExchange.from(request))
                .map(ServerWebExchangeMatcher.MatchResult::isMatch)
                .block();
    }
}

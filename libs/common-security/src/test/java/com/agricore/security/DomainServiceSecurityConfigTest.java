package com.agricore.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

import static org.assertj.core.api.Assertions.assertThat;

class DomainServiceSecurityConfigTest {

    @Test
    void doesNotRequireCsrfForUnsafePostWithoutCookies() {
        assertThat(requiresCsrf(postRequest())).isFalse();
    }

    @Test
    void doesNotRequireCsrfForBearerAuthenticatedPostWithCookie() {
        MockHttpServletRequest request = postRequest();
        addCookie(request);
        request.addHeader("Authorization", "Bearer access-token");

        assertThat(requiresCsrf(request)).isFalse();
    }

    @Test
    void doesNotRequireCsrfForDevHeaderAuthenticatedPostWithCookie() {
        MockHttpServletRequest request = postRequest();
        addCookie(request);
        request.addHeader("X-Dev-User", "developer");
        request.addHeader("X-Dev-Roles", "FARM_MANAGER");

        assertThat(requiresCsrf(request)).isFalse();
    }

    @Test
    void doesNotRequireCsrfForInternalTokenPostWithCookie() {
        MockHttpServletRequest request = postRequest();
        addCookie(request);
        request.addHeader("X-Internal-Service-Token", "internal-token");

        assertThat(requiresCsrf(request)).isFalse();
    }

    @Test
    void requiresCsrfForUnsafePostWithCookie() {
        MockHttpServletRequest request = postRequest();
        addCookie(request);

        assertThat(requiresCsrf(request)).isTrue();
    }

    @Test
    void doesNotTreatEmptyBearerSchemeAsExplicitCredentials() {
        MockHttpServletRequest request = postRequest();
        addCookie(request);
        request.addHeader("Authorization", "Bearer ");

        assertThat(requiresCsrf(request)).isTrue();
    }

    @Test
    void doesNotRequireCsrfForGetWithCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/farms");
        addCookie(request);

        assertThat(requiresCsrf(request)).isFalse();
    }

    @Test
    void rejectingRepositoryDoesNotPersistCsrfState() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/farms");
        MockHttpServletResponse response = new MockHttpServletResponse();
        StatelessRejectingCsrfTokenRepository repository = new StatelessRejectingCsrfTokenRepository();

        CsrfToken generated = repository.generateToken(request);
        repository.saveToken(generated, request, response);

        assertThat(generated.getHeaderName()).isEqualTo("X-AGRICORE-DOMAIN-XSRF-TOKEN");
        assertThat(repository.loadToken(request)).isNull();
        assertThat(response.getCookies()).isEmpty();
        assertThat(request.getSession(false)).isNull();
    }

    private static MockHttpServletRequest postRequest() {
        return new MockHttpServletRequest("POST", "/api/v1/farms");
    }

    private static void addCookie(MockHttpServletRequest request) {
        request.setCookies(new Cookie("ambient-cookie", "value"));
    }

    private static boolean requiresCsrf(MockHttpServletRequest request) {
        return DomainServiceSecurityConfig.COOKIE_BACKED_CSRF_MATCHER.matches(request);
    }
}

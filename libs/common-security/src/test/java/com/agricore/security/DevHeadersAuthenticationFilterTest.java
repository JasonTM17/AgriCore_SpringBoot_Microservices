package com.agricore.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter installs a fully authenticated principal from two request headers, with whatever roles
 * the caller asked for. It is safe only because it is constructed with
 * {@code agricore.security.dev-mode}, which defaults false and is bound to {@code AGRICORE_DEV_MODE}
 * in every service.
 *
 * <p>Nothing tested that. A control that is correct by inspection and unverified at runtime is the
 * exact shape of the account-lockout defect this platform already shipped once, so the disabled case
 * is asserted first and directly.
 */
class DevHeadersAuthenticationFilterTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    /**
     * {@link SecurityContextHolder} is thread-local and JUnit reuses threads. Without this, an
     * authentication left by one test would make a later assertion pass for the wrong reason.
     */
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void installsNothingWhenDisabled() throws Exception {
        MockHttpServletRequest request = devRequest("attacker", "SYSTEM_ADMIN");

        new DevHeadersAuthenticationFilter(false).doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("dev headers must be inert in every deployment that has not opted in")
                .isNull();
    }

    @Test
    void alwaysContinuesTheChain() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = devRequest("attacker", "SYSTEM_ADMIN");

        new DevHeadersAuthenticationFilter(false).doFilter(request, response, chain);

        assertThat(chain.getRequest()).as("a disabled filter must not swallow the request").isNotNull();
    }

    @Test
    void installsPrefixedRolesWhenEnabled() throws Exception {
        MockHttpServletRequest request = devRequest("agronomist", "AGRONOMIST,FARM_MANAGER");

        new DevHeadersAuthenticationFilter(true).doFilter(request, response, new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("agronomist");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_AGRONOMIST", "ROLE_FARM_MANAGER");
    }

    /**
     * Trailing separators and padding are what a hand-written curl produces. They must not become
     * an authority named {@code ROLE_}, which would match nothing yet still look like a grant.
     */
    @Test
    void discardsBlankRoleSegments() throws Exception {
        MockHttpServletRequest request = devRequest("worker", " FIELD_WORKER , , ");

        new DevHeadersAuthenticationFilter(true).doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_FIELD_WORKER");
    }

    /**
     * The JWT path runs first and wins. If the dev filter overwrote an established authentication,
     * enabling dev mode anywhere would let a header downgrade or escalate a verified token.
     */
    @Test
    void doesNotOverwriteAnExistingAuthentication() throws Exception {
        Authentication existing = new TestingAuthenticationToken("real-user", null, "ROLE_FIELD_WORKER");
        SecurityContextHolder.getContext().setAuthentication(existing);

        new DevHeadersAuthenticationFilter(true)
                .doFilter(devRequest("attacker", "SYSTEM_ADMIN"), response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }

    @Test
    void requiresBothHeaders() throws Exception {
        MockHttpServletRequest userOnly = new MockHttpServletRequest();
        userOnly.addHeader("X-Dev-User", "someone");

        new DevHeadersAuthenticationFilter(true).doFilter(userOnly, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsABlankRolesHeader() throws Exception {
        new DevHeadersAuthenticationFilter(true)
                .doFilter(devRequest("someone", "   "), response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static MockHttpServletRequest devRequest(String user, String roles) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Dev-User", user);
        request.addHeader("X-Dev-Roles", roles);
        return request;
    }
}

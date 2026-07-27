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

class DevHeadersAuthenticationFilterTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void installsNothingWhenDisabled() throws Exception {
        MockHttpServletRequest request = devRequest("attacker", "SYSTEM_ADMIN");

        new DevHeadersAuthenticationFilter(false).doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void alwaysContinuesTheChain() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        new DevHeadersAuthenticationFilter(false).doFilter(
                devRequest("attacker", "SYSTEM_ADMIN"),
                response,
                chain
        );

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void installsPrincipalPrefixedRolesAndDerivedPermissionsWhenEnabled() throws Exception {
        MockHttpServletRequest request = devRequest("agronomist", "AGRONOMIST,FARM_MANAGER");

        new DevHeadersAuthenticationFilter(true).doFilter(request, response, new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("agronomist");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains(
                        "ROLE_AGRONOMIST",
                        "ROLE_FARM_MANAGER",
                        "PERMISSION_CROP_CATALOG_WRITE",
                        "PERMISSION_FARM_ADMIN"
                );
    }

    @Test
    void discardsBlankRoleSegments() throws Exception {
        MockHttpServletRequest request = devRequest("worker", " FIELD_WORKER , , ");
        request.addHeader("X-Dev-Permissions", "");

        new DevHeadersAuthenticationFilter(true).doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_FIELD_WORKER");
    }

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

    @Test
    void derivesCanonicalPermissionsWhenExplicitSnapshotIsAbsent() throws Exception {
        MockHttpServletRequest request = devRequest("dev-user", "FIELD_WORKER");

        new DevHeadersAuthenticationFilter(true).doFilter(
                request,
                response,
                new MockFilterChain()
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_FIELD_WORKER", "PERMISSION_WORK_USE", "PERMISSION_IOT_USE")
                .doesNotContain("PERMISSION_WORK_WRITE");
    }

    @Test
    void explicitPermissionHeaderOverridesDerivedRoleGrants() throws Exception {
        MockHttpServletRequest request = devRequest("dev-user", "FIELD_WORKER");
        request.addHeader("X-Dev-Permissions", "WORK_READ");

        new DevHeadersAuthenticationFilter(true).doFilter(
                request,
                response,
                new MockFilterChain()
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("PERMISSION_WORK_READ", "ROLE_FIELD_WORKER");
    }

    @Test
    void explicitEmptyPermissionHeaderRepresentsNoPermissionSnapshot() throws Exception {
        MockHttpServletRequest request = devRequest("dev-user", "FIELD_WORKER");
        request.addHeader("X-Dev-Permissions", "");

        new DevHeadersAuthenticationFilter(true).doFilter(
                request,
                response,
                new MockFilterChain()
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_FIELD_WORKER");
    }

    private static MockHttpServletRequest devRequest(String user, String roles) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Dev-User", user);
        request.addHeader("X-Dev-Roles", roles);
        return request;
    }
}

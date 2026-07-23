package com.agricore.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class DevHeadersAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void derivesCanonicalPermissionsWhenExplicitSnapshotIsAbsent() throws Exception {
        MockHttpServletRequest request = request("FIELD_WORKER");

        new DevHeadersAuthenticationFilter(true).doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_FIELD_WORKER", "PERMISSION_WORK_USE", "PERMISSION_IOT_USE")
                .doesNotContain("PERMISSION_WORK_WRITE");
    }

    @Test
    void explicitPermissionHeaderOverridesDerivedRoleGrants() throws Exception {
        MockHttpServletRequest request = request("FIELD_WORKER");
        request.addHeader("X-Dev-Permissions", "WORK_READ");

        new DevHeadersAuthenticationFilter(true).doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("PERMISSION_WORK_READ", "ROLE_FIELD_WORKER");
    }

    @Test
    void explicitEmptyPermissionHeaderRepresentsNoPermissionSnapshot() throws Exception {
        MockHttpServletRequest request = request("FIELD_WORKER");
        request.addHeader("X-Dev-Permissions", "");

        new DevHeadersAuthenticationFilter(true).doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_FIELD_WORKER");
    }

    @Test
    void disabledFilterDoesNotTrustDevHeaders() throws Exception {
        new DevHeadersAuthenticationFilter(false).doFilter(
                request("SYSTEM_ADMIN"),
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static MockHttpServletRequest request(String roles) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Dev-User", "dev-user");
        request.addHeader("X-Dev-Roles", roles);
        return request;
    }
}

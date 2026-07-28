package com.agricore.identity.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWithDistinctRoleAndPermissionAuthorities() throws Exception {
        JwtTokenService tokenService = mock(JwtTokenService.class);
        Claims claims = Jwts.claims()
                .subject("00000000-0000-0000-0000-000000000001")
                .add("roles", List.of("FIELD_WORKER", "FIELD_WORKER"))
                .add("permissions", List.of("WORK_EXECUTE", "INVENTORY_VIEW", "WORK_EXECUTE"))
                .build();
        when(tokenService.parse("access-token")).thenReturn(claims);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_FIELD_WORKER", "PERMISSION_WORK_EXECUTE", "PERMISSION_INVENTORY_VIEW");
        verify(chain).doFilter(request, response);
    }

    @Test
    void clearsExistingAuthenticationWhenBearerTokenIsInvalid() throws Exception {
        JwtTokenService tokenService = mock(JwtTokenService.class);
        when(tokenService.parse("invalid-token")).thenThrow(new JwtException("invalid JWT"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("previous-user", null, List.of())
        );

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenService).parse("invalid-token");
        verify(chain).doFilter(request, response);
    }

    @Test
    void leavesContextUnauthenticatedWhenBearerHeaderIsAbsent() throws Exception {
        JwtTokenService tokenService = mock(JwtTokenService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenService);
        verify(chain).doFilter(request, response);
    }
}

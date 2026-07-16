package com.agricore.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * ONLY active when agricore.security.dev-mode=true.
 * Never trusts forged JWT payloads without signature verification.
 */
public class DevHeadersAuthenticationFilter extends OncePerRequestFilter {

    private final boolean enabled;

    public DevHeadersAuthenticationFilter(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (enabled && SecurityContextHolder.getContext().getAuthentication() == null) {
            String user = request.getHeader("X-Dev-User");
            String roles = request.getHeader("X-Dev-Roles");
            if (user != null && roles != null && !roles.isBlank()) {
                List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities)
                );
            }
        }
        filterChain.doFilter(request, response);
    }
}

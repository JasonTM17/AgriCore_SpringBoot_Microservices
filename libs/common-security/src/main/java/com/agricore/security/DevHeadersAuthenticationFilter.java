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
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

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
                List<String> roleNames = split(roles);
                String explicitPermissions = request.getHeader("X-Dev-Permissions");
                Collection<String> permissionNames = explicitPermissions == null
                        ? DevRolePermissionCatalog.resolve(roleNames)
                        : split(explicitPermissions);
                List<SimpleGrantedAuthority> authorities = Stream.concat(
                                roleNames.stream().map(role -> "ROLE_" + role),
                                permissionNames.stream().map(permission -> "PERMISSION_" + permission)
                        )
                        .distinct()
                        .sorted()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities)
                );
            }
        }
        filterChain.doFilter(request, response);
    }

    private static List<String> split(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}

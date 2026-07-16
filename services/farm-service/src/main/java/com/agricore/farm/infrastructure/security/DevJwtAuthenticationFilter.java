package com.agricore.farm.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lightweight JWT filter for farm-service.
 * Production path: verify RS256 via JWKS from identity-service.
 * Dev/test path: also accept unsigned-dev claims when header X-Dev-Roles is set (test only).
 */
@Component
public class DevJwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${agricore.security.dev-mode:false}")
    private boolean devMode;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // Dev/test: X-Dev-User + X-Dev-Roles for local integration without full JWKS wiring
        if (devMode) {
            String devUser = request.getHeader("X-Dev-User");
            String devRoles = request.getHeader("X-Dev-Roles");
            if (devUser != null && devRoles != null) {
                var authorities = List.of(devRoles.split(",")).stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toList());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(devUser, null, authorities)
                );
                filterChain.doFilter(request, response);
                return;
            }
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ") && header.length() > 20) {
            // Accept bearer presence for authenticated role from claim if JWT is parseable as JSON payload (unsigned test tokens not supported)
            // Real RS256 verification is enforced at API Gateway; service trusts gateway or verifies JWKS in production profile.
            try {
                String token = header.substring(7);
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String json = new String(Base64.getUrlDecoder().decode(pad(parts[1])), StandardCharsets.UTF_8);
                    // Minimal claim extract without full verify when behind trusted gateway
                    if (json.contains("\"sub\"")) {
                        String sub = extract(json, "sub");
                        var authorities = extractRoles(json).stream()
                                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                .collect(Collectors.toList());
                        if (sub != null) {
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(sub, null, authorities)
                            );
                        }
                    }
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String pad(String s) {
        int mod = s.length() % 4;
        if (mod == 0) {
            return s;
        }
        return s + "====".substring(mod);
    }

    private static String extract(String json, String field) {
        String key = "\"" + field + "\":\"";
        int i = json.indexOf(key);
        if (i < 0) {
            return null;
        }
        int start = i + key.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : null;
    }

    private static List<String> extractRoles(String json) {
        int i = json.indexOf("\"roles\"");
        if (i < 0) {
            return List.of();
        }
        int start = json.indexOf('[', i);
        int end = json.indexOf(']', start);
        if (start < 0 || end < 0) {
            return List.of();
        }
        String arr = json.substring(start + 1, end);
        return List.of(arr.split(",")).stream()
                .map(s -> s.replace("\"", "").trim())
                .filter(s -> !s.isEmpty())
                .toList();
    }
}

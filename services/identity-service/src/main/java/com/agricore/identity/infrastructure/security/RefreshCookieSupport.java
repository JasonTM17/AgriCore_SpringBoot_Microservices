package com.agricore.identity.infrastructure.security;

import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Component
public class RefreshCookieSupport {

    private final SecurityProperties securityProperties;

    public RefreshCookieSupport(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public void requireAllowedBrowserOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            origin = originFromReferer(request.getHeader("Referer"));
        }
        if (origin == null || origin.isBlank()) {
            throw new IdentityException(
                    "ORIGIN_REQUIRED",
                    "Browser auth requires a trusted Origin or Referer header",
                    403
            );
        }
        List<String> allowed = securityProperties.webAllowedOriginList();
        String normalized = normalizeOrigin(origin);
        boolean match = allowed.stream().map(this::normalizeOrigin).anyMatch(normalized::equals);
        if (!match) {
            throw new IdentityException("ORIGIN_FORBIDDEN", "Origin is not allowed for browser auth", 403);
        }
    }

    public ResponseCookie buildRefreshCookie(String rawRefreshToken) {
        return baseCookie(rawRefreshToken, securityProperties.refreshTokenTtlSeconds()).build();
    }

    public ResponseCookie clearRefreshCookie() {
        return baseCookie("", 0).build();
    }

    public String cookieName() {
        return securityProperties.refreshCookieName();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(securityProperties.refreshCookieName(), value == null ? "" : value)
                .httpOnly(true)
                .secure(securityProperties.refreshCookieSecure())
                .path(securityProperties.refreshCookiePath())
                .maxAge(Duration.ofSeconds(Math.max(maxAgeSeconds, 0)))
                .sameSite(normalizeSameSite(securityProperties.refreshCookieSameSite()));
        return builder;
    }

    private static String normalizeSameSite(String sameSite) {
        if (sameSite == null || sameSite.isBlank()) {
            return "Strict";
        }
        String value = sameSite.trim();
        if ("strict".equalsIgnoreCase(value)) {
            return "Strict";
        }
        if ("lax".equalsIgnoreCase(value)) {
            return "Lax";
        }
        if ("none".equalsIgnoreCase(value)) {
            return "None";
        }
        return value;
    }

    private String originFromReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            StringBuilder origin = new StringBuilder();
            origin.append(uri.getScheme().toLowerCase(Locale.ROOT));
            origin.append("://");
            origin.append(uri.getHost().toLowerCase(Locale.ROOT));
            if (uri.getPort() > 0) {
                origin.append(':').append(uri.getPort());
            }
            return origin.toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeOrigin(String origin) {
        try {
            URI uri = URI.create(origin.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (port < 0) {
                return scheme + "://" + host;
            }
            return scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException ex) {
            return origin.trim().toLowerCase(Locale.ROOT);
        }
    }
}

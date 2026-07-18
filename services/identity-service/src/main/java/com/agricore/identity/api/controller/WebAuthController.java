package com.agricore.identity.api.controller;

import com.agricore.identity.api.request.LoginRequest;
import com.agricore.identity.api.response.AuthTokensResponse;
import com.agricore.identity.api.response.WebAuthTokensResponse;
import com.agricore.identity.application.service.AuthApplicationService;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import com.agricore.identity.infrastructure.security.RefreshCookieSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browser-only auth surface. Refresh credentials travel only as HttpOnly cookies;
 * JSON bodies never include the refresh token.
 */
@RestController
@RequestMapping("/api/v1/auth/web")
public class WebAuthController {

    private final AuthApplicationService authService;
    private final SecurityProperties securityProperties;
    private final RefreshCookieSupport refreshCookieSupport;

    public WebAuthController(
            AuthApplicationService authService,
            SecurityProperties securityProperties,
            RefreshCookieSupport refreshCookieSupport
    ) {
        this.authService = authService;
        this.securityProperties = securityProperties;
        this.refreshCookieSupport = refreshCookieSupport;
    }

    @PostMapping("/login")
    public ResponseEntity<WebAuthTokensResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest http
    ) {
        refreshCookieSupport.requireAllowedBrowserOrigin(http);
        AuthTokensResponse tokens = authService.login(request, clientIp(http), http.getHeader("User-Agent"));
        ResponseCookie cookie = refreshCookieSupport.buildRefreshCookie(tokens.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(WebAuthTokensResponse.from(tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<WebAuthTokensResponse> refresh(HttpServletRequest http) {
        refreshCookieSupport.requireAllowedBrowserOrigin(http);
        String refreshCookie = readRefreshCookie(http);
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new IdentityException("INVALID_REFRESH_TOKEN", "Refresh session cookie is missing", 401);
        }
        AuthTokensResponse tokens = authService.refresh(refreshCookie, clientIp(http), http.getHeader("User-Agent"));
        ResponseCookie cookie = refreshCookieSupport.buildRefreshCookie(tokens.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(WebAuthTokensResponse.from(tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        refreshCookieSupport.requireAllowedBrowserOrigin(http);
        authService.logout(readRefreshCookie(http));
        ResponseCookie cleared = refreshCookieSupport.clearRefreshCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String expected = refreshCookieSupport.cookieName();
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (expected.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (securityProperties.trustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}

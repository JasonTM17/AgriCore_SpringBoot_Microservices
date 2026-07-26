package com.agricore.identity.api.controller;

import com.agricore.identity.api.request.LoginRequest;
import com.agricore.identity.api.request.LogoutRequest;
import com.agricore.identity.api.request.RefreshRequest;
import com.agricore.identity.api.request.RegisterRequest;
import com.agricore.identity.api.response.AuthTokensResponse;
import com.agricore.identity.api.response.UserResponse;
import com.agricore.identity.application.service.AuthApplicationService;
import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService authService;
    private final SecurityProperties securityProperties;

    public AuthController(AuthApplicationService authService, SecurityProperties securityProperties) {
        this.authService = authService;
        this.securityProperties = securityProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthTokensResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, clientIp(http), http.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    public AuthTokensResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(request.refreshToken(), clientIp(http), http.getHeader("User-Agent"));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    /**
     * Resolves the address the rate limiter keys on.
     *
     * <p>Reads the <em>rightmost</em> {@code X-Forwarded-For} entry, not the leftmost. The gateway
     * appends the peer address it observed rather than replacing the header, so everything to the
     * left of the last entry was supplied by the caller and can be forged. Keying on the leftmost
     * value would let a client mint a fresh rate-limit bucket per request by rotating the header,
     * which removes the brute-force brake entirely.
     *
     * <p>This is correct for exactly one trusted proxy in front of identity. Adding a second hop
     * means dropping a known number of trailing entries instead.
     */
    private String clientIp(HttpServletRequest request) {
        if (securityProperties.trustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                for (int i = hops.length - 1; i >= 0; i--) {
                    String hop = hops[i].trim();
                    if (!hop.isEmpty()) {
                        return hop;
                    }
                }
            }
        }
        return request.getRemoteAddr();
    }
}

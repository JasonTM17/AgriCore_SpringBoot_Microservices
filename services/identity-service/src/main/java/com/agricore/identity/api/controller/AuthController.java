package com.agricore.identity.api.controller;

import com.agricore.identity.api.request.LoginRequest;
import com.agricore.identity.api.request.LogoutRequest;
import com.agricore.identity.api.request.RefreshRequest;
import com.agricore.identity.api.request.RegisterRequest;
import com.agricore.identity.api.response.AuthTokensResponse;
import com.agricore.identity.api.response.UserResponse;
import com.agricore.identity.application.service.AuthApplicationService;
import com.agricore.identity.infrastructure.security.SignedClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService authService;
    private final SignedClientIpResolver clientIpResolver;

    public AuthController(AuthApplicationService authService, SignedClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthTokensResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(
                request,
                clientIpResolver.resolve(http),
                http.getHeader("User-Agent")
        );
    }

    @PostMapping("/refresh")
    public AuthTokensResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(
                request.refreshToken(),
                clientIpResolver.resolve(http),
                http.getHeader("User-Agent")
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

}

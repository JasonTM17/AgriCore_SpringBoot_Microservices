package com.agricore.identity.api.controller;

import com.agricore.identity.api.response.UserResponse;
import com.agricore.identity.application.service.AuthApplicationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthApplicationService authService;

    public UserController(AuthApplicationService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return authService.me(userId);
    }
}

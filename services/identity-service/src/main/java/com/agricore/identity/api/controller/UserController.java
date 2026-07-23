package com.agricore.identity.api.controller;

import com.agricore.identity.api.response.UserResponse;
import com.agricore.identity.application.service.AuthApplicationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
        List<String> roleSnapshot = snapshotAuthorities(authentication, "ROLE_");
        List<String> permissionSnapshot = snapshotAuthorities(authentication, "PERMISSION_");
        return authService.me(userId, roleSnapshot, permissionSnapshot);
    }

    private static List<String> snapshotAuthorities(Authentication authentication, String prefix) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith(prefix))
                .map(authority -> authority.substring(prefix.length()))
                .distinct()
                .sorted()
                .toList();
    }
}

package com.agricore.identity.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.identity.api.request.UpdateUserRolesRequest;
import com.agricore.identity.api.response.UserResponse;
import com.agricore.identity.application.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminUserService.listUsers(PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()));
    }

    @PatchMapping("/{userId}/roles")
    public UserResponse updateRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        return adminUserService.updateRoles(userId, request.roles());
    }
}

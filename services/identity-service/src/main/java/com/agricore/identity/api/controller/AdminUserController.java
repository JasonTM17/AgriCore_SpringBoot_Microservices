package com.agricore.identity.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.identity.api.request.UpdateUserRolesRequest;
import com.agricore.identity.api.response.UserResponse;
import com.agricore.identity.application.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_IDENTITY_USER_READ')")
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        return adminUserService.listUsers(PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()));
    }

    @PatchMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('PERMISSION_IDENTITY_USER_ADMIN')")
    public UserResponse updateRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        return adminUserService.updateRoles(userId, request.roles());
    }
}

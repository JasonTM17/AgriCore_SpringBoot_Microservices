package com.agricore.identity.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.identity.api.request.UpdateRolePermissionsRequest;
import com.agricore.identity.api.response.PermissionResponse;
import com.agricore.identity.api.response.RolePermissionsResponse;
import com.agricore.identity.application.service.AdminPermissionService;
import com.agricore.identity.domain.model.RoleCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminPermissionController {

    private final AdminPermissionService permissionService;

    public AdminPermissionController(AdminPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/permissions")
    public PageResponse<PermissionResponse> listPermissions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        return permissionService.listPermissions(
                PageRequest.of(page, Math.min(size, 100), Sort.by("code").ascending())
        );
    }

    @GetMapping("/roles/{roleCode}/permissions")
    public RolePermissionsResponse getRolePermissions(@PathVariable RoleCode roleCode) {
        return permissionService.getRolePermissions(roleCode);
    }

    @PutMapping("/roles/{roleCode}/permissions")
    public RolePermissionsResponse replaceRolePermissions(
            @PathVariable RoleCode roleCode,
            @Valid @RequestBody UpdateRolePermissionsRequest request,
            Authentication authentication
    ) {
        return permissionService.replaceRolePermissions(
                roleCode,
                request.permissionCodes(),
                request.expectedVersion(),
                request.reason(),
                authentication.getName()
        );
    }
}

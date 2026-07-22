package com.agricore.identity.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.identity.api.request.CreatePermissionRequest;
import com.agricore.identity.api.response.PermissionResponse;
import com.agricore.identity.api.response.RolePermissionsResponse;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.domain.model.RoleCode;
import com.agricore.identity.infrastructure.persistence.PermissionJpaRepository;
import com.agricore.identity.infrastructure.persistence.RoleJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.PermissionEntity;
import com.agricore.identity.infrastructure.persistence.entity.RoleEntity;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminPermissionService {

    private static final String PERMISSION_CODE_UNIQUE_CONSTRAINT = "uk_permissions_code";

    private final PermissionJpaRepository permissionRepository;
    private final RoleJpaRepository roleRepository;

    public AdminPermissionService(
            PermissionJpaRepository permissionRepository,
            RoleJpaRepository roleRepository
    ) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PermissionResponse> listPermissions(Pageable pageable) {
        Page<PermissionEntity> page = permissionRepository.findAll(pageable);
        List<PermissionResponse> content = page.getContent().stream()
                .map(AdminPermissionService::toResponse)
                .toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        String code = request.code().trim();
        if (permissionRepository.existsByCodeIgnoreCase(code)) {
            throw permissionExists(code);
        }

        PermissionEntity permission = new PermissionEntity();
        permission.setId(UUID.randomUUID());
        permission.setCode(code);
        permission.setName(request.name().trim());
        permission.setDescription(trimToNull(request.description()));
        permission.setCreatedAt(Instant.now());
        try {
            return toResponse(permissionRepository.saveAndFlush(permission));
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, PERMISSION_CODE_UNIQUE_CONSTRAINT)) {
                throw permissionExists(code);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public RolePermissionsResponse getRolePermissions(RoleCode roleCode) {
        RoleEntity role = roleRepository.findByCode(roleCode.name())
                .orElseThrow(() -> roleNotFound(roleCode));
        return toRoleResponse(role);
    }

    @Transactional
    public RolePermissionsResponse replaceRolePermissions(RoleCode roleCode, Set<String> permissionCodes) {
        RoleEntity role = roleRepository.findByCodeForUpdate(roleCode.name())
                .orElseThrow(() -> roleNotFound(roleCode));
        List<PermissionEntity> permissions = permissionRepository.findAllByCodeIn(permissionCodes);
        if (permissions.size() != permissionCodes.size()) {
            Set<String> resolvedCodes = permissions.stream()
                    .map(PermissionEntity::getCode)
                    .collect(java.util.stream.Collectors.toSet());
            String missingCode = permissionCodes.stream()
                    .filter(code -> !resolvedCodes.contains(code))
                    .sorted()
                    .findFirst()
                    .orElse("unknown");
            throw new IdentityException(
                    "PERMISSION_NOT_FOUND",
                    "Permission not found: " + missingCode,
                    404
            );
        }
        role.setPermissions(new HashSet<>(permissions));
        return toRoleResponse(roleRepository.saveAndFlush(role));
    }

    private static RolePermissionsResponse toRoleResponse(RoleEntity role) {
        List<PermissionResponse> permissions = role.getPermissions().stream()
                .sorted(java.util.Comparator.comparing(PermissionEntity::getCode))
                .map(AdminPermissionService::toResponse)
                .toList();
        return new RolePermissionsResponse(role.getCode(), permissions);
    }

    private static PermissionResponse toResponse(PermissionEntity permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getCreatedAt()
        );
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static IdentityException permissionExists(String code) {
        return new IdentityException("PERMISSION_EXISTS", "Permission already exists: " + code, 409);
    }

    private static IdentityException roleNotFound(RoleCode roleCode) {
        return new IdentityException("ROLE_NOT_FOUND", "Role not found: " + roleCode.name(), 404);
    }

    private static boolean hasConstraint(Throwable exception, String constraintName) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}

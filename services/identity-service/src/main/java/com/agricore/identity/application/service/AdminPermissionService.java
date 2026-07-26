package com.agricore.identity.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.identity.api.response.PermissionResponse;
import com.agricore.identity.api.response.RolePermissionsResponse;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.domain.model.RoleCode;
import com.agricore.identity.infrastructure.persistence.PermissionJpaRepository;
import com.agricore.identity.infrastructure.persistence.RoleJpaRepository;
import com.agricore.identity.infrastructure.persistence.RolePermissionPolicyAuditJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.PermissionEntity;
import com.agricore.identity.infrastructure.persistence.entity.RoleEntity;
import com.agricore.identity.infrastructure.persistence.entity.RolePermissionPolicyAuditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminPermissionService {

    private static final String POLICY_ADMIN_PERMISSION_CODE = "IDENTITY_POLICY_ADMIN";

    private final PermissionJpaRepository permissionRepository;
    private final RoleJpaRepository roleRepository;
    private final RolePermissionPolicyAuditJpaRepository auditRepository;

    public AdminPermissionService(
            PermissionJpaRepository permissionRepository,
            RoleJpaRepository roleRepository,
            RolePermissionPolicyAuditJpaRepository auditRepository
    ) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PermissionResponse> listPermissions(Pageable pageable) {
        Page<PermissionEntity> page = permissionRepository.findAllByAssignableTrue(pageable);
        List<PermissionResponse> content = page.getContent().stream()
                .map(AdminPermissionService::toResponse)
                .toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public RolePermissionsResponse getRolePermissions(RoleCode roleCode) {
        RoleEntity role = roleRepository.findByCode(roleCode.name())
                .orElseThrow(() -> roleNotFound(roleCode));
        return toRoleResponse(role);
    }

    @Transactional
    public RolePermissionsResponse replaceRolePermissions(
            RoleCode roleCode,
            Set<String> permissionCodes,
            long expectedVersion,
            String reason,
            String actorSubject
    ) {
        RoleEntity role = roleRepository.findByCodeForUpdate(roleCode.name())
                .orElseThrow(() -> roleNotFound(roleCode));
        if (role.getPermissionPolicyVersion() != expectedVersion) {
            throw new IdentityException(
                    "POLICY_VERSION_CONFLICT",
                    "Role permission policy was modified; reload and retry",
                    409
            );
        }
        validateBreakGlassPolicy(roleCode, permissionCodes);

        List<PermissionEntity> requestedPermissions = permissionCodes.isEmpty()
                ? List.of()
                : permissionRepository.findAllByCodeInAndAssignableTrue(permissionCodes);
        validateAllPermissionsExist(permissionCodes, requestedPermissions);

        List<String> before = sortedAssignableCodes(role.getPermissions());
        List<String> after = requestedPermissions.stream()
                .map(PermissionEntity::getCode)
                .sorted()
                .toList();

        Set<PermissionEntity> preservedLegacyPermissions = role.getPermissions().stream()
                .filter(permission -> !permission.isAssignable())
                .collect(Collectors.toCollection(HashSet::new));
        preservedLegacyPermissions.addAll(requestedPermissions);

        long nextVersion = expectedVersion + 1;
        role.setPermissions(preservedLegacyPermissions);
        role.setPermissionPolicyVersion(nextVersion);
        RoleEntity updatedRole = roleRepository.saveAndFlush(role);

        RolePermissionPolicyAuditEntity audit = new RolePermissionPolicyAuditEntity();
        audit.setId(UUID.randomUUID());
        audit.setRole(updatedRole);
        audit.setPolicyVersion(nextVersion);
        audit.setActorSubject(actorSubject.trim());
        audit.setChangedAt(Instant.now());
        audit.setReason(reason.trim());
        audit.setBeforePermissions(toJsonArray(before));
        audit.setAfterPermissions(toJsonArray(after));
        auditRepository.saveAndFlush(audit);

        return toRoleResponse(updatedRole);
    }

    private static void validateBreakGlassPolicy(RoleCode roleCode, Set<String> permissionCodes) {
        if (roleCode == RoleCode.SYSTEM_ADMIN && !permissionCodes.contains(POLICY_ADMIN_PERMISSION_CODE)) {
            throw new IdentityException(
                    "SYSTEM_ADMIN_POLICY_ADMIN_REQUIRED",
                    "SYSTEM_ADMIN must retain " + POLICY_ADMIN_PERMISSION_CODE,
                    409
            );
        }
    }

    private static void validateAllPermissionsExist(
            Set<String> permissionCodes,
            List<PermissionEntity> permissions
    ) {
        if (permissions.size() == permissionCodes.size()) {
            return;
        }
        Set<String> resolvedCodes = permissions.stream()
                .map(PermissionEntity::getCode)
                .collect(Collectors.toSet());
        String missingCode = permissionCodes.stream()
                .filter(code -> !resolvedCodes.contains(code))
                .sorted()
                .findFirst()
                .orElse("unknown");
        throw new IdentityException(
                "PERMISSION_NOT_FOUND",
                "Canonical permission not found: " + missingCode,
                404
        );
    }

    private static RolePermissionsResponse toRoleResponse(RoleEntity role) {
        List<PermissionResponse> permissions = role.getPermissions().stream()
                .filter(PermissionEntity::isAssignable)
                .sorted(Comparator.comparing(PermissionEntity::getCode))
                .map(AdminPermissionService::toResponse)
                .toList();
        return new RolePermissionsResponse(
                role.getCode(),
                role.getPermissionPolicyVersion(),
                permissions
        );
    }

    private static PermissionResponse toResponse(PermissionEntity permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getCreatedAt(),
                permission.getCatalogVersion()
        );
    }

    private static List<String> sortedAssignableCodes(Set<PermissionEntity> permissions) {
        return permissions.stream()
                .filter(PermissionEntity::isAssignable)
                .map(PermissionEntity::getCode)
                .sorted()
                .toList();
    }

    private static String toJsonArray(List<String> permissionCodes) {
        return permissionCodes.stream()
                .map(code -> "\"" + code + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static IdentityException roleNotFound(RoleCode roleCode) {
        return new IdentityException("ROLE_NOT_FOUND", "Role not found: " + roleCode.name(), 404);
    }
}

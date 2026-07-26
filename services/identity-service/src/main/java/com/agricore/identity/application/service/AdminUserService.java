package com.agricore.identity.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.identity.api.response.UserResponse;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.domain.model.RoleCode;
import com.agricore.identity.domain.model.UserStatus;
import com.agricore.identity.infrastructure.persistence.RoleJpaRepository;
import com.agricore.identity.infrastructure.persistence.UserJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.RoleEntity;
import com.agricore.identity.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final EffectivePermissionService effectivePermissionService;

    public AdminUserService(
            UserJpaRepository userRepository,
            RoleJpaRepository roleRepository,
            EffectivePermissionService effectivePermissionService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.effectivePermissionService = effectivePermissionService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Pageable pageable) {
        Page<UserEntity> page = userRepository.findAll(pageable);
        var permissionsByUser = effectivePermissionService.resolveForUsers(page.getContent());
        List<UserResponse> content = page.getContent().stream()
                .map(user -> toResponse(user, permissionsByUser.getOrDefault(user.getId(), List.of())))
                .toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional
    public UserResponse updateRoles(UUID userId, Set<RoleCode> roleCodes) {
        RoleEntity systemAdminRole = roleRepository.findByCodeForUpdate(RoleCode.SYSTEM_ADMIN.name())
                .orElseThrow(() -> new IdentityException(
                        "ROLE_MISSING",
                        "Role not seeded: " + RoleCode.SYSTEM_ADMIN.name(),
                        500
                ));
        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IdentityException("USER_NOT_FOUND", "User not found", 404));
        requireActiveSystemAdministratorRemains(user, roleCodes);

        Set<RoleEntity> roles = new HashSet<>();
        for (RoleCode code : roleCodes) {
            RoleEntity role = code == RoleCode.SYSTEM_ADMIN
                    ? systemAdminRole
                    : roleRepository.findByCode(code.name())
                            .orElseThrow(() -> new IdentityException(
                                    "ROLE_MISSING",
                                    "Role not seeded: " + code.name(),
                                    500
                            ));
            roles.add(role);
        }

        user.setRoles(roles);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        return toResponse(user, effectivePermissionService.resolveForRoles(
                roles.stream().map(RoleEntity::getCode).toList()
        ));
    }

    private void requireActiveSystemAdministratorRemains(
            UserEntity user,
            Set<RoleCode> requestedRoles
    ) {
        boolean removesSystemAdmin = user.getStatus() == UserStatus.ACTIVE
                && user.getRoles().stream()
                        .anyMatch(role -> RoleCode.SYSTEM_ADMIN.name().equals(role.getCode()))
                && !requestedRoles.contains(RoleCode.SYSTEM_ADMIN);
        if (removesSystemAdmin
                && userRepository.countActiveUsersByRoleCode(RoleCode.SYSTEM_ADMIN.name()) <= 1) {
            throw new IdentityException(
                    "LAST_SYSTEM_ADMIN_REQUIRED",
                    "At least one active SYSTEM_ADMIN must remain",
                    409
            );
        }
    }

    private UserResponse toResponse(UserEntity user, List<String> permissions) {
        List<String> roles = user.getRoles().stream().map(RoleEntity::getCode).sorted().collect(Collectors.toList());
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus().name(),
                roles,
                permissions,
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}

package com.agricore.identity.application.service;

import com.agricore.identity.infrastructure.persistence.PermissionJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.RoleEntity;
import com.agricore.identity.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EffectivePermissionService {

    private final PermissionJpaRepository permissionRepository;

    public EffectivePermissionService(PermissionJpaRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public List<String> resolveForRoles(Collection<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return List.of();
        }
        return permissionRepository.findGrantedCodesByRoleCodes(roleCodes);
    }

    public Map<UUID, List<String>> resolveForUsers(Collection<UserEntity> users) {
        Set<String> roleCodes = users.stream()
                .flatMap(user -> user.getRoles().stream())
                .map(RoleEntity::getCode)
                .collect(Collectors.toSet());
        if (roleCodes.isEmpty()) {
            return users.stream().collect(Collectors.toMap(UserEntity::getId, ignored -> List.of()));
        }

        Map<String, Set<String>> permissionsByRole = new HashMap<>();
        for (PermissionJpaRepository.RolePermissionGrant grant
                : permissionRepository.findGrantedCodesGroupedByRoleCodes(roleCodes)) {
            permissionsByRole
                    .computeIfAbsent(grant.getRoleCode(), ignored -> new TreeSet<>())
                    .add(grant.getPermissionCode());
        }

        Map<UUID, List<String>> result = new HashMap<>();
        for (UserEntity user : users) {
            Set<String> effective = new TreeSet<>();
            for (RoleEntity role : user.getRoles()) {
                effective.addAll(permissionsByRole.getOrDefault(role.getCode(), Set.of()));
            }
            result.put(user.getId(), List.copyOf(effective));
        }
        return result;
    }
}

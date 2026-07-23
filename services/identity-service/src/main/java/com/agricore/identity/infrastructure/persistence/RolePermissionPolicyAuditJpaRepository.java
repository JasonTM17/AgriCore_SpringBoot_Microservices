package com.agricore.identity.infrastructure.persistence;

import com.agricore.identity.infrastructure.persistence.entity.RolePermissionPolicyAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RolePermissionPolicyAuditJpaRepository
        extends JpaRepository<RolePermissionPolicyAuditEntity, UUID> {

    List<RolePermissionPolicyAuditEntity> findAllByRoleIdOrderByPolicyVersionAsc(UUID roleId);
}

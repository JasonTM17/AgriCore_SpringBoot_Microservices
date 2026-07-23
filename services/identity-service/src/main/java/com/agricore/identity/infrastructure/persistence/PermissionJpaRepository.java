package com.agricore.identity.infrastructure.persistence;

import com.agricore.identity.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, UUID> {
    Optional<PermissionEntity> findByCodeIgnoreCase(String code);

    Page<PermissionEntity> findAllByAssignableTrue(Pageable pageable);

    List<PermissionEntity> findAllByCodeInAndAssignableTrue(Collection<String> codes);

    @Query("""
            SELECT DISTINCT permission.code
            FROM RoleEntity role
            JOIN role.permissions permission
            WHERE role.code IN :roleCodes
              AND permission.assignable = true
            ORDER BY permission.code
            """)
    List<String> findGrantedCodesByRoleCodes(@Param("roleCodes") Collection<String> roleCodes);

    @Query("""
            SELECT role.code AS roleCode, permission.code AS permissionCode
            FROM RoleEntity role
            JOIN role.permissions permission
            WHERE role.code IN :roleCodes
              AND permission.assignable = true
            ORDER BY role.code, permission.code
            """)
    List<RolePermissionGrant> findGrantedCodesGroupedByRoleCodes(
            @Param("roleCodes") Collection<String> roleCodes
    );

    interface RolePermissionGrant {
        String getRoleCode();

        String getPermissionCode();
    }
}

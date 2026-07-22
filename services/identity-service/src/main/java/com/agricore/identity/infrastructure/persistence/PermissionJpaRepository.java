package com.agricore.identity.infrastructure.persistence;

import com.agricore.identity.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, UUID> {
    Optional<PermissionEntity> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<PermissionEntity> findAllByCodeIn(Collection<String> codes);

    @Query("""
            SELECT DISTINCT permission.code
            FROM RoleEntity role
            JOIN role.permissions permission
            WHERE role.code IN :roleCodes
            ORDER BY permission.code
            """)
    List<String> findGrantedCodesByRoleCodes(@Param("roleCodes") Collection<String> roleCodes);
}

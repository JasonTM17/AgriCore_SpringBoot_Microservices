package com.agricore.identity;

import com.agricore.identity.infrastructure.persistence.PermissionJpaRepository;
import com.agricore.identity.infrastructure.persistence.RoleJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.PermissionEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class PermissionPersistenceIntegrationTest {

    @Autowired
    private PermissionJpaRepository permissionRepository;

    @Autowired
    private RoleJpaRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void legacyRoleGrantPersistsButIsNotAnEffectiveCanonicalPermission() {
        PermissionEntity permission = new PermissionEntity();
        permission.setId(UUID.randomUUID());
        permission.setCode("LEGACY_WORK_EXECUTE");
        permission.setName("Legacy execute work tasks");
        permission.setDescription("Created before the migration-owned canonical catalog");
        permission.setCreatedAt(Instant.now());
        permissionRepository.saveAndFlush(permission);

        var role = roleRepository.findByCode("FIELD_WORKER").orElseThrow();
        role.setPermissions(Set.of(permission));
        roleRepository.saveAndFlush(role);
        entityManager.clear();

        var reloadedRole = roleRepository.findByCode("FIELD_WORKER").orElseThrow();
        assertThat(reloadedRole.getPermissions())
                .extracting(PermissionEntity::getCode)
                .containsExactly("LEGACY_WORK_EXECUTE");
        assertThat(permissionRepository.findByCodeIgnoreCase("legacy_work_execute"))
                .isPresent();
        assertThat(permissionRepository.findGrantedCodesByRoleCodes(List.of("FIELD_WORKER")))
                .isEmpty();
    }
}

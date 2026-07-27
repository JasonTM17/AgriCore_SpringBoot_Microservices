package com.agricore.identity;

import com.agricore.identity.infrastructure.persistence.PermissionJpaRepository;
import com.agricore.identity.infrastructure.persistence.RoleJpaRepository;
import com.agricore.identity.infrastructure.persistence.RolePermissionPolicyAuditJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.PermissionEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
class AdminPermissionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PermissionJpaRepository permissionRepository;

    @Autowired
    private RoleJpaRepository roleRepository;

    @Autowired
    private RolePermissionPolicyAuditJpaRepository auditRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @WithMockUser(authorities = {
            "ROLE_SYSTEM_ADMIN", "PERMISSION_IDENTITY_POLICY_READ", "PERMISSION_IDENTITY_POLICY_ADMIN"
    })
    void catalogAndSeededRolePoliciesAreDeterministicAndSorted() throws Exception {
        mockMvc.perform(get("/api/v1/admin/permissions").queryParam("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(32))
                .andExpect(jsonPath("$.content[0].code").value("ASSISTANT_USE"))
                .andExpect(jsonPath("$.content[0].catalogVersion").value(1))
                .andExpect(jsonPath("$.content[31].code").value("WORK_WRITE"));

        var assistant = permissionRepository.findByCodeIgnoreCase("ASSISTANT_USE").orElseThrow();
        assertThat(assistant.getId())
                .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222032"));
        assertThat(assistant.isAssignable()).isTrue();

        mockMvc.perform(get("/api/v1/admin/roles/SYSTEM_ADMIN/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.permissions.length()").value(32));

        mockMvc.perform(get("/api/v1/admin/roles/AUDITOR/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.permissions.length()").value(12));

        var auditor = roleRepository.findByCode("AUDITOR").orElseThrow();
        assertThat(auditor.getPermissions())
                .filteredOn(permission -> permission.isAssignable())
                .extracting(PermissionEntity::getCode)
                .allMatch(code -> code.endsWith("_READ"));
    }

    @Test
    @WithMockUser(username = "admin-subject", authorities = {
            "ROLE_SYSTEM_ADMIN", "PERMISSION_IDENTITY_POLICY_READ", "PERMISSION_IDENTITY_POLICY_ADMIN"
    })
    void versionedReplacementIsAtomicAuditedAndRejectsStaleWrites() throws Exception {
        PermissionEntity legacyPermission = new PermissionEntity();
        legacyPermission.setId(UUID.fromString("33333333-3333-3333-3333-333333333334"));
        legacyPermission.setCode("LEGACY_RUNTIME_GRANT");
        legacyPermission.setName("Legacy runtime grant");
        legacyPermission.setCreatedAt(Instant.now());
        permissionRepository.saveAndFlush(legacyPermission);
        var roleWithLegacyGrant = roleRepository.findByCode("FIELD_WORKER").orElseThrow();
        var grantsWithLegacy = new HashSet<>(roleWithLegacyGrant.getPermissions());
        grantsWithLegacy.add(legacyPermission);
        roleWithLegacyGrant.setPermissions(grantsWithLegacy);
        roleRepository.saveAndFlush(roleWithLegacyGrant);

        mockMvc.perform(put("/api/v1/admin/roles/FIELD_WORKER/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCodes":["WORK_USE","FARM_READ"],
                                  "expectedVersion":1,
                                  "reason":"Limit worker policy for seasonal contractors"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FIELD_WORKER"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.permissions[0].code").value("FARM_READ"))
                .andExpect(jsonPath("$.permissions[1].code").value("WORK_USE"));

        var role = roleRepository.findByCode("FIELD_WORKER").orElseThrow();
        var audits = auditRepository.findAllByRoleIdOrderByPolicyVersionAsc(role.getId());
        assertThat(audits).hasSize(1);
        var audit = audits.getFirst();
        assertThat(audit.getPolicyVersion()).isEqualTo(2);
        assertThat(audit.getActorSubject()).isEqualTo("admin-subject");
        assertThat(audit.getReason()).isEqualTo("Limit worker policy for seasonal contractors");
        assertThat(audit.getChangedAt()).isNotNull();
        assertThat(audit.getBeforePermissions()).contains("\"ASSISTANT_USE\"", "\"WORK_USE\"");
        assertThat(audit.getAfterPermissions()).isEqualTo("[\"FARM_READ\",\"WORK_USE\"]");

        mockMvc.perform(put("/api/v1/admin/roles/FIELD_WORKER/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCodes":["ASSISTANT_USE"],
                                  "expectedVersion":1,
                                  "reason":"Stale browser form"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POLICY_VERSION_CONFLICT"));

        mockMvc.perform(put("/api/v1/admin/roles/FIELD_WORKER/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCodes":["LEGACY_RUNTIME_GRANT"],
                                  "expectedVersion":2,
                                  "reason":"Legacy non-assignable code must not partially apply"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERMISSION_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/admin/roles/FIELD_WORKER/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.permissions[0].code").value("FARM_READ"))
                .andExpect(jsonPath("$.permissions[1].code").value("WORK_USE"));
        assertThat(auditRepository.findAllByRoleIdOrderByPolicyVersionAsc(role.getId())).hasSize(1);

        entityManager.clear();
        var reloadedRole = roleRepository.findByCode("FIELD_WORKER").orElseThrow();
        assertThat(reloadedRole.getPermissions())
                .extracting(PermissionEntity::getCode)
                .contains("FARM_READ", "WORK_USE", "LEGACY_RUNTIME_GRANT");
        assertThat(permissionRepository.findGrantedCodesByRoleCodes(List.of("FIELD_WORKER")))
                .containsExactly("FARM_READ", "WORK_USE");
    }

    @Test
    @WithMockUser(username = "break-glass-admin", authorities = {
            "ROLE_SYSTEM_ADMIN", "PERMISSION_IDENTITY_POLICY_READ", "PERMISSION_IDENTITY_POLICY_ADMIN"
    })
    void systemAdminReplacementCannotRemovePolicyAdminPermission() throws Exception {
        var role = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();

        mockMvc.perform(put("/api/v1/admin/roles/SYSTEM_ADMIN/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCodes":["IDENTITY_POLICY_READ"],
                                  "expectedVersion":1,
                                  "reason":"Accidental self-lockout"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYSTEM_ADMIN_POLICY_ADMIN_REQUIRED"));

        entityManager.clear();
        var unchanged = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();
        assertThat(unchanged.getPermissionPolicyVersion()).isEqualTo(1);
        assertThat(unchanged.getPermissions())
                .extracting(PermissionEntity::getCode)
                .contains("IDENTITY_POLICY_ADMIN");
        assertThat(auditRepository.findAllByRoleIdOrderByPolicyVersionAsc(role.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "break-glass-admin", authorities = {
            "ROLE_SYSTEM_ADMIN", "PERMISSION_IDENTITY_POLICY_READ", "PERMISSION_IDENTITY_POLICY_ADMIN"
    })
    void systemAdminReplacementSucceedsWhenPolicyAdminPermissionIsRetained() throws Exception {
        mockMvc.perform(put("/api/v1/admin/roles/SYSTEM_ADMIN/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCodes":["IDENTITY_POLICY_ADMIN","IDENTITY_POLICY_READ"],
                                  "expectedVersion":1,
                                  "reason":"Retain break-glass administration"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SYSTEM_ADMIN"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.permissions[0].code").value("IDENTITY_POLICY_ADMIN"))
                .andExpect(jsonPath("$.permissions[1].code").value("IDENTITY_POLICY_READ"));

        var role = roleRepository.findByCode("SYSTEM_ADMIN").orElseThrow();
        assertThat(auditRepository.findAllByRoleIdOrderByPolicyVersionAsc(role.getId())).hasSize(1);
    }

    @Test
    @WithMockUser(authorities = {
            "ROLE_SYSTEM_ADMIN", "PERMISSION_IDENTITY_POLICY_READ", "PERMISSION_IDENTITY_POLICY_ADMIN"
    })
    void runtimePermissionCreationIsNotSupported() throws Exception {
        long before = permissionRepository.count();

        mockMvc.perform(post("/api/v1/admin/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"RUNTIME_INERT","name":"Runtime inert permission"}
                                """))
                .andExpect(status().isMethodNotAllowed());

        assertThat(permissionRepository.count()).isEqualTo(before);
        assertThat(permissionRepository.findByCodeIgnoreCase("RUNTIME_INERT")).isEmpty();
    }

    @Test
    @WithMockUser(roles = "FARM_MANAGER")
    void nonAdminCannotReadOrReplacePermissionPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/admin/permissions"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/roles/FIELD_WORKER/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCodes":["WORK_USE"],
                                  "expectedVersion":1,
                                  "reason":"Forbidden policy change"
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}

package com.agricore.identity;

import com.agricore.identity.infrastructure.persistence.PermissionJpaRepository;
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

import java.util.UUID;

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

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdminManagesPermissionAndAtomicallyReplacesRoleGrants() throws Exception {
        String permissionCode = uniquePermissionCode();
        String createBody = """
                {
                  "code":"%s",
                  "name":"Execute work tasks",
                  "description":"Complete field work with material usage"
                }
                """.formatted(permissionCode);

        mockMvc.perform(post("/api/v1/admin/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(permissionCode))
                .andExpect(jsonPath("$.name").value("Execute work tasks"));

        mockMvc.perform(post("/api/v1/admin/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERMISSION_EXISTS"));

        mockMvc.perform(get("/api/v1/admin/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.code == '%s')]".formatted(permissionCode)).exists())
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(put("/api/v1/admin/roles/FIELD_WORKER/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionCodes":["%s"]}
                                """.formatted(permissionCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FIELD_WORKER"))
                .andExpect(jsonPath("$.permissions[0].code").value(permissionCode));

        mockMvc.perform(put("/api/v1/admin/roles/FIELD_WORKER/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionCodes":["DOES_NOT_EXIST"]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERMISSION_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/admin/roles/FIELD_WORKER/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0].code").value(permissionCode));

        mockMvc.perform(put("/api/v1/admin/roles/FIELD_WORKER/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionCodes":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    @WithMockUser(roles = "FARM_MANAGER")
    void nonAdminCannotCreatePermissions() throws Exception {
        String permissionCode = uniquePermissionCode();

        mockMvc.perform(post("/api/v1/admin/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"Forbidden permission"}
                                """.formatted(permissionCode)))
                .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(
                permissionRepository.findByCodeIgnoreCase(permissionCode)
        ).isEmpty();
    }

    private static String uniquePermissionCode() {
        return "WORK_EXECUTE_" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(java.util.Locale.ROOT);
    }
}

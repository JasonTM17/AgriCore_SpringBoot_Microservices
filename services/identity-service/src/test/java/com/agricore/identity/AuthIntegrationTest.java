package com.agricore.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginRefreshLogout_flow() throws Exception {
        String email = "worker" + System.nanoTime() + "@agricore.test";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!","fullName":"Field Worker"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("FIELD_WORKER"))
                .andExpect(jsonPath("$.permissions[0]").value("ASSISTANT_USE"))
                .andExpect(jsonPath("$.permissions[12]").value("WORK_USE"));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.permissions[0]").value("ASSISTANT_USE"))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.get("accessToken").asText();
        String refreshToken = loginJson.get("refreshToken").asText();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.permissions[0]").value("ASSISTANT_USE"))
                .andExpect(jsonPath("$.permissions[12]").value("WORK_USE"));

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newRefresh = refreshJson.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refreshToken);

        // Old refresh token must fail (rotated)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(newRefresh)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        String email = "badlogin" + System.nanoTime() + "@agricore.test";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!","fullName":"Bad Login User"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"WrongPass1!"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_repeatedFailuresPersistAccountLockout() throws Exception {
        String email = "lockout" + System.nanoTime() + "@agricore.test";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!","fullName":"Lockout User"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"WrongPass1!"}
                                    """.formatted(email)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void loginValidation_doesNotEchoRejectedInputs() throws Exception {
        String rejectedPassword = "do-not-echo-" + "x".repeat(128);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"worker@agricore.test","password":"%s"}
                                """.formatted(rejectedPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(rejectedPassword)
                .doesNotContain("rejectedValue");
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String email = "dup" + System.nanoTime() + "@agricore.test";
        String body = """
                {"email":"%s","password":"Secret123!","fullName":"Dup"}
                """.formatted(email);
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @Transactional
    void currentUserKeepsJwtPermissionSnapshotUntilANewTokenIsIssued() throws Exception {
        String email = "snapshot" + System.nanoTime() + "@agricore.test";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!","fullName":"Snapshot User"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        MvcResult firstLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        String originalToken = objectMapper.readTree(firstLogin.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        mockMvc.perform(put("/api/v1/admin/roles/FIELD_WORKER/permissions")
                        .with(user("policy-admin").roles("SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCodes":["WORK_USE"],
                                  "expectedVersion":1,
                                  "reason":"Snapshot compatibility test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + originalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(13))
                .andExpect(jsonPath("$.permissions[0]").value("ASSISTANT_USE"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.permissions.length()").value(1))
                .andExpect(jsonPath("$.user.permissions[0]").value("WORK_USE"));
    }

    @Test
    @Transactional
    void currentUserKeepsJwtRoleSnapshotUntilANewTokenIsIssued() throws Exception {
        String email = "role-snapshot" + System.nanoTime() + "@agricore.test";
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!","fullName":"Role Snapshot User"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = objectMapper.readTree(registration.getResponse().getContentAsString())
                .get("id")
                .asText();

        MvcResult firstLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("FIELD_WORKER"))
                .andReturn();
        String originalToken = objectMapper.readTree(firstLogin.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        mockMvc.perform(patch("/api/v1/admin/users/{userId}/roles", userId)
                        .with(user("policy-admin").roles("SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["AUDITOR"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("AUDITOR"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + originalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("FIELD_WORKER"))
                .andExpect(jsonPath("$.permissions.length()").value(13));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles.length()").value(1))
                .andExpect(jsonPath("$.user.roles[0]").value("AUDITOR"))
                .andExpect(jsonPath("$.user.permissions.length()").value(12));
    }

    @Test
    @Transactional
    void adminUserResponseIncludesSortedLiveEffectivePermissions() throws Exception {
        String email = "admin-view" + System.nanoTime() + "@agricore.test";
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!","fullName":"Admin View User"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = objectMapper.readTree(registration.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(patch("/api/v1/admin/users/{userId}/roles", userId)
                        .with(user("policy-admin").roles("SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["AUDITOR"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("AUDITOR"))
                .andExpect(jsonPath("$.permissions.length()").value(12))
                .andExpect(jsonPath("$.permissions[0]").value("CROP_CATALOG_READ"))
                .andExpect(jsonPath("$.permissions[11]").value("WORK_READ"));
    }
}

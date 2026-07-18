package com.agricore.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class IdentityErrorBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void malformedJson_returnsStableBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void unsupportedMediaType_returns415() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void invalidUuid_returnsStableBadRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/not-a-uuid/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"AUDITOR\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void invalidPaging_returnsValidationError() throws Exception {
        for (String query : List.of("?page=-1", "?size=0")) {
            mockMvc.perform(get("/api/v1/admin/users" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void invalidRoleSets_returnValidationError() throws Exception {
        UUID userId = registerUser();

        for (String body : List.of("{\"roles\":[]}", "{\"roles\":[null]}")) {
            mockMvc.perform(patch("/api/v1/admin/users/{userId}/roles", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void unknownRole_returnsMalformedJson() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{userId}/roles", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROOT\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    private UUID registerUser() throws Exception {
        String email = "boundary" + System.nanoTime() + "@agricore.test";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!","fullName":"Boundary User"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        return UUID.fromString(id);
    }
}

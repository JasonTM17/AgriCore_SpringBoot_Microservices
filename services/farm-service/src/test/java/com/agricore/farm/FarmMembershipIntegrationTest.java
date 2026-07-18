package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmMembershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ownerCanGrantAndRevoke_withoutOrphaningFarm() throws Exception {
        String owner = subject();
        String worker = subject();
        String intruder = subject();
        String farmId = createFarm(owner);

        MvcResult initialMemberships = mockMvc.perform(get("/api/v1/farms/{farmId}/memberships", farmId)
                        .headers(devAuth(owner, "FARM_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        String ownerMembershipId = membershipId(initialMemberships, owner);

        mockMvc.perform(delete("/api/v1/farms/{farmId}/memberships/{membershipId}", farmId, ownerMembershipId)
                        .headers(devAuth(owner, "FARM_MANAGER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_FARM_MEMBERSHIP"));

        mockMvc.perform(post("/api/v1/farms/{farmId}/memberships", farmId)
                        .headers(devAuth(owner, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));

        grant(farmId, owner, worker)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value(worker));

        grant(farmId, owner, worker)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARM_MEMBERSHIP_EXISTS"));

        mockMvc.perform(get("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth(worker, "FIELD_WORKER")))
                .andExpect(status().isOk());

        grant(farmId, worker, subject(), "FIELD_WORKER")
                .andExpect(status().isForbidden());
        grant(farmId, intruder, subject())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_DENIED"));

        MvcResult memberships = mockMvc.perform(get("/api/v1/farms/{farmId}/memberships", farmId)
                        .headers(devAuth(owner, "FARM_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        String workerMembershipId = membershipId(memberships, worker);

        mockMvc.perform(delete("/api/v1/farms/{farmId}/memberships/{membershipId}", farmId, workerMembershipId)
                        .headers(devAuth(owner, "FARM_MANAGER")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth(worker, "FIELD_WORKER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_DENIED"));
    }

    @Test
    void systemAdminCanManageMembership_withoutExistingMembership() throws Exception {
        String owner = subject();
        String worker = subject();
        String farmId = createFarm(owner);

        grant(farmId, "system-admin", worker, "SYSTEM_ADMIN")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/farms/{farmId}", farmId)
                        .headers(devAuth(worker, "FIELD_WORKER")))
                .andExpect(status().isOk());
    }

    private String createFarm(String owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/farms")
                        .headers(devAuth(owner, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"F-%s","name":"Membership Farm"}
                                """.formatted(UUID.randomUUID().toString().replace("-", ""))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions grant(
            String farmId,
            String actor,
            String subject
    ) throws Exception {
        return grant(farmId, actor, subject, "FARM_MANAGER");
    }

    private org.springframework.test.web.servlet.ResultActions grant(
            String farmId,
            String actor,
            String subject,
            String role
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/farms/{farmId}/memberships", farmId)
                .headers(devAuth(actor, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"subject":"%s"}
                        """.formatted(subject)));
    }

    private String membershipId(MvcResult result, String subject) throws Exception {
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        for (JsonNode membership : content) {
            if (subject.equals(membership.get("subject").asText())) {
                return membership.get("id").asText();
            }
        }
        throw new AssertionError("Missing membership for " + subject);
    }

    private static org.springframework.http.HttpHeaders devAuth(String subject, String role) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Dev-User", subject);
        headers.set("X-Dev-Roles", role);
        return headers;
    }

    private static String subject() {
        return UUID.randomUUID().toString();
    }
}

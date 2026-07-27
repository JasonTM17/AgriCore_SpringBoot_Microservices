package com.agricore.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssistantServiceFoundationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void capabilitiesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/capabilities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void capabilitiesReportUnavailableProviderWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/capabilities")
                        .header("X-Dev-User", "manager-1")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("none"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.streaming").value(false))
                .andExpect(jsonPath("$.reasonCode").value("AI_PROVIDER_UNAVAILABLE"));
    }
}

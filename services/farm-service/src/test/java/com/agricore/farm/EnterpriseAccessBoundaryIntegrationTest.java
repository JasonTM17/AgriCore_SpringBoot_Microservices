package com.agricore.farm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnterpriseAccessBoundaryIntegrationTest extends EnterpriseApiTestSupport {

    @Test
    void requiresAuthenticationAndSystemAdministratorRole() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/enterprises")
                        .headers(devAuth(compactId(), "FARM_MANAGER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/enterprises")
                        .headers(devAuth(compactId(), "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                validEnterpriseRequest("DENIED", "Denied", "TAX-DENIED")
                        )))
                .andExpect(status().isForbidden());
    }
}

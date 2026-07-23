package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnterpriseManagementIntegrationTest extends EnterpriseApiTestSupport {

    @Test
    void createsReadsAndUpdatesAuditedEnterpriseData() throws Exception {
        JsonNode created = createEnterprise("agrico-op", "Agri Co-op", "tax-100");
        String enterpriseId = created.get("id").asText();

        assertEquals("AGRICO-OP", created.get("code").asText());
        assertEquals("TAX-100", created.get("taxCode").asText());
        assertEquals("ACTIVE", created.get("status").asText());
        assertEquals("system-admin", created.get("createdBy").asText());
        assertEquals(0, created.get("version").asLong());

        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}", enterpriseId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Agri Co-op"));

        mockMvc.perform(patch("/api/v1/enterprises/{enterpriseId}", enterpriseId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0,
                                  "name": "Agri Cooperative",
                                  "legalName": null,
                                  "taxCode": "tax-101",
                                  "address": "",
                                  "province": "Dak Lak",
                                  "status": "INACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Agri Cooperative"))
                .andExpect(jsonPath("$.legalName").doesNotExist())
                .andExpect(jsonPath("$.taxCode").value("TAX-101"))
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.province").value("Dak Lak"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.updatedBy").value("system-admin"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void filtersLiterallyAndPaginatesEqualNamesDeterministically() throws Exception {
        createEnterprise("ENT-A", "Shared Name", "TAX-A");
        createEnterprise("ENT-B", "Shared Name", "TAX-B");
        createEnterprise("ENT-C", "Other Name", "TAX-C");

        mockMvc.perform(get("/api/v1/enterprises")
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .queryParam("province", "lam")
                        .queryParam("q", "shared")
                        .queryParam("status", "active")
                        .queryParam("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        for (String query : new String[]{"%", "_", "!"}) {
            mockMvc.perform(get("/api/v1/enterprises")
                            .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                            .queryParam("q", query))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        Set<String> ids = new HashSet<>();
        for (int page = 0; page < 2; page++) {
            String body = mockMvc.perform(get("/api/v1/enterprises")
                            .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                            .queryParam("q", "shared")
                            .queryParam("page", Integer.toString(page))
                            .queryParam("size", "1")
                            .queryParam("sort", "name,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            ids.add(objectMapper.readTree(body).get("content").get(0).get("id").asText());
        }
        assertEquals(2, ids.size());
    }
}

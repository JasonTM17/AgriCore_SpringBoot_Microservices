package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class FarmEnterpriseApiTestSupport extends EnterpriseApiTestSupport {

    protected JsonNode createFarm(
            String subject,
            String role,
            String name,
            String enterpriseId
    ) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("code", "F-" + compactId());
        request.put("name", name);
        request.put("province", "Lam Dong");
        if (enterpriseId != null) {
            request.put("enterpriseId", enterpriseId);
        }
        String body = mockMvc.perform(post("/api/v1/farms")
                        .headers(devAuth(subject, role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    protected JsonNode createLinkedFarm(String enterpriseId) throws Exception {
        return createFarm(
                "system-admin",
                "SYSTEM_ADMIN",
                "Enterprise Farm",
                enterpriseId
        );
    }
}

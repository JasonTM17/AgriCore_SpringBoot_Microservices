package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class FarmAreaApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String createFarm(String owner) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("code", "F-" + compactId());
        request.put("name", "Area Test Farm");
        String body = mockMvc.perform(post("/api/v1/farms")
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    protected JsonNode createArea(
            String owner,
            String farmId,
            String code,
            String name,
            double hectares
    ) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("code", code);
        request.put("name", name);
        request.put("areaInHectares", hectares);
        String body = mockMvc.perform(post("/api/v1/farms/{farmId}/areas", farmId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    protected JsonNode createPlot(String owner, String farmId, String areaId) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("code", "P-" + compactId());
        request.put("name", "Area Plot");
        request.put("areaInHectares", 1.25);
        if (areaId != null) {
            request.put("areaId", areaId);
        }
        String body = mockMvc.perform(post("/api/v1/farms/{farmId}/plots", farmId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    protected static HttpHeaders devAuth(String subject, String... roles) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Dev-User", subject);
        headers.set("X-Dev-Roles", roles.length == 0 ? "FARM_MANAGER" : String.join(",", roles));
        return headers;
    }

    protected static String compactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

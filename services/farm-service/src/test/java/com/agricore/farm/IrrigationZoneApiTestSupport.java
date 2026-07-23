package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class IrrigationZoneApiTestSupport extends FarmAreaApiTestSupport {

    protected JsonNode createIrrigationZone(
            String owner,
            String plotId,
            String code,
            String name,
            String method,
            double flowRate
    ) throws Exception {
        String body = mockMvc.perform(post("/api/v1/plots/{plotId}/irrigation-zones", plotId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                validIrrigationZoneRequest(code, name, method, flowRate)
                        )))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    protected ObjectNode validIrrigationZoneRequest(
            String code,
            String name,
            String method,
            double flowRate
    ) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("code", code);
        request.put("name", name);
        request.put("method", method);
        request.put("flowRateLitersPerMinute", flowRate);
        request.put("targetMoisturePercent", 35.0);
        request.put("notes", "Primary irrigation line");
        return request;
    }
}

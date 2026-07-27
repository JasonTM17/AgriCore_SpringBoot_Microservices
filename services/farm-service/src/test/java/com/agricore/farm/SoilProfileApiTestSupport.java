package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class SoilProfileApiTestSupport extends FarmAreaApiTestSupport {

    protected JsonNode createSoilProfile(
            String owner,
            String plotId,
            String sampleCode,
            LocalDate sampledAt,
            double ph
    ) throws Exception {
        ObjectNode request = validSoilProfileRequest(sampleCode, sampledAt, ph);
        String body = mockMvc.perform(post("/api/v1/plots/{plotId}/soil-profiles", plotId)
                        .headers(devAuth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    protected ObjectNode validSoilProfileRequest(
            String sampleCode,
            LocalDate sampledAt,
            double ph
    ) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("sampleCode", sampleCode);
        request.put("sampledAt", sampledAt.toString());
        request.put("sampleDepthCm", 20.0);
        request.put("texture", "CLAY_LOAM");
        request.put("ph", ph);
        request.put("organicMatterPercent", 3.25);
        request.put("nitrogenMgKg", 42.5);
        request.put("phosphorusMgKg", 18.75);
        request.put("potassiumMgKg", 165.0);
        request.put("moisturePercent", 31.5);
        request.put("notes", "Composite laboratory sample");
        return request;
    }
}

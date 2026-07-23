package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class EnterpriseApiTestSupport extends FarmAreaApiTestSupport {

    protected JsonNode createEnterprise(
            String code,
            String name,
            String taxCode
    ) throws Exception {
        String body = mockMvc.perform(post("/api/v1/enterprises")
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                validEnterpriseRequest(code, name, taxCode)
                        )))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    protected ObjectNode validEnterpriseRequest(
            String code,
            String name,
            String taxCode
    ) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("code", code);
        request.put("name", name);
        request.put("legalName", name + " Agricultural Company");
        request.put("taxCode", taxCode);
        request.put("address", "1 Orchard Road");
        request.put("province", "Lam Dong");
        return request;
    }
}

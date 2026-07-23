package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnterpriseValidationIntegrationTest extends EnterpriseApiTestSupport {

    @Test
    void rejectsDuplicatesInvalidCodesAndUnknownFields() throws Exception {
        createEnterprise("ENT-VALID", "Valid Enterprise", "TAX-VALID");
        assertCreateConflict(
                validEnterpriseRequest("ent-valid", "Duplicate Code", "TAX-OTHER"),
                "ENTERPRISE_CODE_EXISTS"
        );
        assertCreateConflict(
                validEnterpriseRequest("ENT-OTHER", "Duplicate Tax", "tax-valid"),
                "ENTERPRISE_TAX_CODE_EXISTS"
        );

        ObjectNode invalidCode = validEnterpriseRequest(
                "INVALID CODE",
                "Invalid",
                "TAX-INVALID"
        );
        mockMvc.perform(post("/api/v1/enterprises")
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalidCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        ObjectNode unknown = validEnterpriseRequest("ENT-UNKNOWN", "Unknown", "TAX-UNKNOWN");
        unknown.put("legalTitle", "Misspelled");
        mockMvc.perform(post("/api/v1/enterprises")
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(unknown)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void rejectsEmptyNullStaleDuplicateTaxAndOverflowingUpdates() throws Exception {
        JsonNode first = createEnterprise("ENT-FIRST", "First", "TAX-FIRST");
        createEnterprise("ENT-SECOND", "Second", "TAX-SECOND");
        String path = "/api/v1/enterprises/{enterpriseId}";

        assertPatchError(first, """
                {"version":0}
                """, "ENTERPRISE_EMPTY_UPDATE", 400);
        assertPatchError(first, """
                {"version":0,"name":null}
                """, "ENTERPRISE_FIELD_REQUIRED", 400);
        assertPatchError(first, """
                {"version":0,"unknown":"value"}
                """, "MALFORMED_JSON", 400);
        assertPatchError(first, """
                {"version":0,"taxCode":"tax-second"}
                """, "ENTERPRISE_TAX_CODE_EXISTS", 409);

        mockMvc.perform(patch(path, first.get("id").asText())
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"status":"INACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertPatchError(first, """
                {"version":0,"status":"ACTIVE"}
                """, "ENTERPRISE_VERSION_CONFLICT", 409);

        mockMvc.perform(get("/api/v1/enterprises")
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .queryParam("page", "10001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void assertCreateConflict(ObjectNode request, String code) throws Exception {
        mockMvc.perform(post("/api/v1/enterprises")
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code));
    }

    private void assertPatchError(
            JsonNode enterprise,
            String content,
            String code,
            int statusCode
    ) throws Exception {
        mockMvc.perform(patch(
                                "/api/v1/enterprises/{enterpriseId}",
                                enterprise.get("id").asText()
                        )
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.code").value(code));
    }
}

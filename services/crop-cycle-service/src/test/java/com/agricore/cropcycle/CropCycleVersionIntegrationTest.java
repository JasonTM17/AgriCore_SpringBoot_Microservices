package com.agricore.cropcycle;

import com.agricore.farmaccess.FarmAccessClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CropCycleVersionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void stageResponse_returnsCommittedVersion() throws Exception {
        MvcResult created = mockMvc.perform(authenticated(post("/api/v1/crop-cycles"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdBody = readBody(created);
        String cycleId = createdBody.required("id").asText();
        long createdVersion = createdBody.required("version").asLong();

        MvcResult changed = mockMvc.perform(authenticated(post("/api/v1/crop-cycles/" + cycleId + "/stage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"LAND_PREPARATION"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long changedVersion = readBody(changed).required("version").asLong();

        MvcResult reloaded = mockMvc.perform(authenticated(get("/api/v1/crop-cycles/" + cycleId)))
                .andExpect(status().isOk())
                .andReturn();
        long reloadedVersion = readBody(reloaded).required("version").asLong();

        assertThat(changedVersion).isEqualTo(createdVersion + 1);
        assertThat(reloadedVersion).isEqualTo(changedVersion);
    }

    private String createRequest() {
        return """
                {
                  "code":"VERSION-%d",
                  "farmId":"%s",
                  "plotId":"%s",
                  "cropId":"%s",
                  "plannedStartDate":"2026-03-01",
                  "plannedEndDate":"2026-11-30"
                }
                """.formatted(System.nanoTime(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request
                .header("X-Dev-User", "agronomist")
                .header("X-Dev-Roles", "AGRONOMIST");
    }
}

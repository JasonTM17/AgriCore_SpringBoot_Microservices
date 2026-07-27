package com.agricore.iot;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IotAlertDedupTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void burstLowSoilMoisture_raisesSingleOpenAlertUnderCooldown() throws Exception {
        String deviceCode = "DEV-" + System.nanoTime();
        mockMvc.perform(post("/api/v1/iot/devices")
                        .header("X-Dev-User", "ag")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode":"%s","plotId":"%s","name":"Soil probe A"}
                                """.formatted(deviceCode, UUID.randomUUID())))
                .andExpect(status().isCreated());

        String reading = """
                {
                  "deviceCode":"%s",
                  "metricType":"SOIL_MOISTURE",
                  "metricValue":10.5,
                  "unit":"PCT"
                }
                """.formatted(deviceCode);

        MvcResult first = mockMvc.perform(post("/api/v1/iot/readings")
                        .header("X-Dev-User", "ag")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reading))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertRaised").value(true))
                .andExpect(jsonPath("$.alertStatus").value("OPEN"))
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        String alertId = firstJson.get("alertId").asText();

        // Burst of low readings within cooldown must not open another alert
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/iot/readings")
                            .header("X-Dev-User", "ag")
                            .header("X-Dev-Roles", "AGRONOMIST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reading))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alertRaised").value(false))
                    .andExpect(jsonPath("$.alertId").value(alertId));
        }
    }
}

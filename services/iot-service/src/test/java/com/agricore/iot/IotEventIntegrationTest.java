package com.agricore.iot;

import com.agricore.common.event.EventTypes;
import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.iot.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.iot.infrastructure.persistence.entity.OutboxEventEntity;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IotEventIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void readingsAlwaysEmitAndOnlyANewAlertEmitsThresholdExceeded() throws Exception {
        String deviceCode = ("EVENT-" + UUID.randomUUID()).toUpperCase();
        UUID plotId = UUID.randomUUID();
        register(deviceCode, plotId);

        ingest(deviceCode, "50.0000", false);
        ingest(deviceCode, "10.5000", true);
        ingest(deviceCode, "10.0000", false);

        List<OutboxEventEntity> events = outboxRepository.findAll().stream()
                .filter(event -> event.getPayload().contains("\"deviceCode\":\"" + deviceCode + "\""))
                .toList();
        assertThat(events).filteredOn(event -> EventTypes.SENSOR_READING_RECEIVED.equals(event.getEventType()))
                .hasSize(3);
        assertThat(events).filteredOn(event -> EventTypes.SENSOR_THRESHOLD_EXCEEDED.equals(event.getEventType()))
                .singleElement();
        for (OutboxEventEntity event : events) {
            JsonNode envelope = objectMapper.readTree(event.getPayload());
            assertThat(envelope.path("eventId").asText()).isEqualTo(event.getId().toString());
            assertThat(envelope.path("producer").asText()).isEqualTo("iot-service");
            assertThat(envelope.path("payload").path("deviceCode").asText()).isEqualTo(deviceCode);
            assertThat(envelope.path("payload").path("plotId").asText()).isEqualTo(plotId.toString());
        }
    }

    @Test
    void stableReadingIdMakesRedeliveryIdempotent() throws Exception {
        String deviceCode = ("DEDUPE-" + UUID.randomUUID()).toUpperCase();
        UUID plotId = UUID.randomUUID();
        UUID readingId = UUID.randomUUID();
        register(deviceCode, plotId);

        ingestWithId(deviceCode, readingId, "50.0000", "Reading accepted");
        ingestWithId(deviceCode, readingId, "50.0000", "Duplicate reading ignored");

        assertThat(outboxRepository.findAll()).filteredOn(event ->
                        EventTypes.SENSOR_READING_RECEIVED.equals(event.getEventType())
                                && event.getPayload().contains("\"deviceCode\":\"" + deviceCode + "\""))
                .singleElement();
    }

    @Test
    void rejectsStableReadingIdReusedForDifferentTelemetry() throws Exception {
        String deviceCode = ("CONFLICT-" + UUID.randomUUID()).toUpperCase();
        UUID plotId = UUID.randomUUID();
        UUID readingId = UUID.randomUUID();
        register(deviceCode, plotId);

        ingestWithId(deviceCode, readingId, "50.0000", "Reading accepted");

        mockMvc.perform(post("/api/v1/iot/readings")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "readingId":"%s",
                                  "deviceCode":"%s",
                                  "metricType":"SOIL_MOISTURE",
                                  "metricValue":49.0000,
                                  "unit":"PCT"
                                }
                                """.formatted(readingId, deviceCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("READING_ID_CONFLICT"));

        assertThat(outboxRepository.findAll()).filteredOn(event ->
                        EventTypes.SENSOR_READING_RECEIVED.equals(event.getEventType())
                                && event.getPayload().contains("\"deviceCode\":\"" + deviceCode + "\""))
                .singleElement();
    }

    private void register(String deviceCode, UUID plotId) throws Exception {
        mockMvc.perform(post("/api/v1/iot/devices")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode":"%s","plotId":"%s","name":"Soil probe"}
                                """.formatted(deviceCode, plotId)))
                .andExpect(status().isCreated());
    }

    private void ingest(String deviceCode, String value, boolean alertRaised) throws Exception {
        mockMvc.perform(post("/api/v1/iot/readings")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceCode":"%s",
                                  "metricType":"SOIL_MOISTURE",
                                  "metricValue":%s,
                                  "unit":"PCT"
                                }
                                """.formatted(deviceCode, value)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertRaised").value(alertRaised));
    }

    private void ingestWithId(String deviceCode, UUID readingId, String value, String message) throws Exception {
        mockMvc.perform(post("/api/v1/iot/readings")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "readingId":"%s",
                                  "deviceCode":"%s",
                                  "metricType":"SOIL_MOISTURE",
                                  "metricValue":%s,
                                  "unit":"PCT"
                                }
                                """.formatted(readingId, deviceCode, value)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(message));
    }
}

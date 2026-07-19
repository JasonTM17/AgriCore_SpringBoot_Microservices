package com.agricore.iot;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.iot.infrastructure.persistence.DeviceJpaRepository;
import com.agricore.iot.infrastructure.persistence.SensorAlertJpaRepository;
import com.agricore.iot.infrastructure.persistence.SensorReadingJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IotAccessFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DeviceJpaRepository deviceRepository;
    @Autowired
    private SensorReadingJpaRepository readingRepository;
    @Autowired
    private SensorAlertJpaRepository alertRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @ParameterizedTest
    @MethodSource("farmAccessFailures")
    void register_whenFarmAccessFails_writesNoDevice(HttpStatus status, String code) throws Exception {
        UUID plotId = UUID.randomUUID();
        long devicesBefore = deviceRepository.count();
        doThrow(farmError(status, code)).when(farmAccessClient).requirePlot(plotId);

        assertApiError(
                mockMvc.perform(post("/api/v1/iot/devices")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("DENIED-" + System.nanoTime(), plotId))),
                status,
                code
        );

        assertThat(deviceRepository.count()).isEqualTo(devicesBefore);
    }

    @ParameterizedTest
    @MethodSource("farmAccessFailures")
    void ingest_whenFarmAccessFails_mutatesNeitherDeviceReadingNorAlert(
            HttpStatus status,
            String code
    ) throws Exception {
        UUID plotId = UUID.randomUUID();
        String deviceCode = "INGEST-" + System.nanoTime();
        UUID deviceId = registerAccepted(deviceCode, plotId);
        Instant lastSeenBefore = deviceRepository.findById(deviceId).orElseThrow().getLastSeenAt();
        long readingsBefore = readingRepository.count();
        long alertsBefore = alertRepository.count();
        clearInvocations(farmAccessClient);
        doThrow(farmError(status, code)).when(farmAccessClient).requirePlot(plotId);

        String body = assertApiError(
                mockMvc.perform(post("/api/v1/iot/readings")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(readingRequest(deviceCode))),
                status,
                code
        ).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(deviceCode);
        assertThat(deviceRepository.findById(deviceId).orElseThrow().getLastSeenAt()).isEqualTo(lastSeenBefore);
        assertThat(readingRepository.count()).isEqualTo(readingsBefore);
        assertThat(alertRepository.count()).isEqualTo(alertsBefore);
    }

    @Test
    void register_whenRoleIsInsufficient_returnsStructuredErrorBeforeFarmLookup() throws Exception {
        assertApiError(
                mockMvc.perform(post("/api/v1/iot/devices")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest("ROLE-" + System.nanoTime(), UUID.randomUUID()))),
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
        );

        verifyNoInteractions(farmAccessClient);
    }

    private UUID registerAccepted(String deviceCode, UUID plotId) throws Exception {
        mockMvc.perform(post("/api/v1/iot/devices")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest(deviceCode, plotId)))
                .andExpect(status().isCreated());
        return deviceRepository.findByDeviceCodeIgnoreCase(deviceCode).orElseThrow().getId();
    }

    private static String registerRequest(String deviceCode, UUID plotId) {
        return """
                {"deviceCode":"%s","plotId":"%s","name":"Soil probe"}
                """.formatted(deviceCode, plotId);
    }

    private static String readingRequest(String deviceCode) {
        return """
                {"deviceCode":"%s","metricType":"SOIL_MOISTURE","metricValue":10.5,"unit":"PCT"}
                """.formatted(deviceCode);
    }

    private static ResultActions assertApiError(
            ResultActions result,
            HttpStatus expectedStatus,
            String expectedCode
    ) throws Exception {
        return result.andExpect(status().is(expectedStatus.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(expectedStatus.value()))
                .andExpect(jsonPath("$.error").value(expectedStatus.getReasonPhrase()))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.path").isString());
    }

    private static FarmAccessException farmError(HttpStatus status, String code) {
        return new FarmAccessException(code, "Farm access rejected", status.value());
    }

    private static Stream<Arguments> farmAccessFailures() {
        return Stream.of(
                Arguments.of(HttpStatus.FORBIDDEN, "FARM_ACCESS_DENIED"),
                Arguments.of(HttpStatus.NOT_FOUND, "FARM_RESOURCE_NOT_FOUND"),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, "FARM_ACCESS_UNAVAILABLE")
        );
    }
}

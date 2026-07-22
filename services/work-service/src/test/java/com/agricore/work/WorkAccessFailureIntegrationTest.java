package com.agricore.work;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.work.domain.model.TaskStatus;
import com.agricore.work.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkAccessFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private WorkTaskJpaRepository taskRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @ParameterizedTest
    @MethodSource("farmAccessFailures")
    void create_whenFarmAccessFails_writesNeitherTaskNorOutbox(
            HttpStatus status,
            String code
    ) throws Exception {
        UUID plotId = UUID.randomUUID();
        long tasksBefore = taskRepository.count();
        long outboxBefore = outboxRepository.count();
        doThrow(farmError(status, code)).when(farmAccessClient).requirePlot(plotId);

        assertApiError(
                mockMvc.perform(post("/api/v1/work-tasks")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest("DENIED-" + System.nanoTime(), plotId))),
                status,
                code
        );

        assertThat(taskRepository.count()).isEqualTo(tasksBefore);
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
    }

    @Test
    void get_whenFarmAccessIsMaskedNotFound_doesNotLeakTaskData() throws Exception {
        String taskCode = "GET-" + System.nanoTime();
        UUID plotId = UUID.randomUUID();
        UUID taskId = createAccepted(taskCode, plotId);
        doThrow(farmError(HttpStatus.NOT_FOUND, "FARM_RESOURCE_NOT_FOUND"))
                .when(farmAccessClient).requirePlot(plotId);

        String body = assertApiError(
                mockMvc.perform(get("/api/v1/work-tasks/{taskId}", taskId)
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")),
                HttpStatus.NOT_FOUND,
                "FARM_RESOURCE_NOT_FOUND"
        ).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(taskCode);
    }

    @Test
    void complete_whenFarmAccessIsDenied_preservesTaskAndOutbox() throws Exception {
        UUID plotId = UUID.randomUUID();
        UUID taskId = createAccepted("COMPLETE-" + System.nanoTime(), plotId);
        long outboxBefore = outboxRepository.count();
        doThrow(farmError(HttpStatus.FORBIDDEN, "FARM_ACCESS_DENIED"))
                .when(farmAccessClient).requirePlot(plotId);

        assertApiError(
                mockMvc.perform(post("/api/v1/work-tasks/{taskId}/complete", taskId)
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"should not persist\"}")),
                HttpStatus.FORBIDDEN,
                "FARM_ACCESS_DENIED"
        );

        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.CREATED);
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"start", "cancel"})
    void lifecycleMutation_whenFarmAccessIsDenied_preservesTask(String action) throws Exception {
        UUID plotId = UUID.randomUUID();
        UUID taskId = createAccepted(action.toUpperCase() + "-" + System.nanoTime(), plotId);
        long outboxBefore = outboxRepository.count();
        doThrow(farmError(HttpStatus.FORBIDDEN, "FARM_ACCESS_DENIED"))
                .when(farmAccessClient).requirePlot(plotId);

        assertApiError(
                mockMvc.perform(post("/api/v1/work-tasks/{taskId}/{action}", taskId, action)
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")),
                HttpStatus.FORBIDDEN,
                "FARM_ACCESS_DENIED"
        );

        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.CREATED);
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore);
    }

    @Test
    void create_whenRoleIsInsufficient_returnsStructuredErrorBeforeFarmLookup() throws Exception {
        assertApiError(
                mockMvc.perform(post("/api/v1/work-tasks")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest("ROLE-" + System.nanoTime(), UUID.randomUUID()))),
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED"
        );

        verifyNoInteractions(farmAccessClient);
    }

    private UUID createAccepted(String code, UUID plotId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/work-tasks")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(code, plotId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private static String createRequest(String code, UUID plotId) {
        return """
                {"code":"%s","cropCycleId":"%s","plotId":"%s",
                 "taskType":"IRRIGATION","title":"Boundary task","priority":"HIGH"}
                """.formatted(code, UUID.randomUUID(), plotId);
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

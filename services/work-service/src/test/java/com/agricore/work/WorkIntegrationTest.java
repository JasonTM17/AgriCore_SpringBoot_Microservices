package com.agricore.work;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmResourceAccess;
import com.agricore.work.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkIntegrationTest {

    private static final UUID FARM_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @BeforeEach
    void authorizePlotAccess() {
        when(farmAccessClient.requirePlot(any(UUID.class)))
                .thenAnswer(invocation -> new FarmResourceAccess(FARM_ID, invocation.getArgument(0)));
    }

    @Test
    void createAssignStartComplete_flow() throws Exception {
        MvcResult created = createTask();

        String taskId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        OutboxEventEntity taskCreated = outboxRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(taskId))
                .findFirst()
                .orElseThrow();
        JsonNode taskCreatedEnvelope = objectMapper.readTree(taskCreated.getPayload());
        assertThat(taskCreatedEnvelope.path("eventId").asText()).isEqualTo(taskCreated.getId().toString());
        UUID employee = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/work-tasks/" + taskId + "/assign")
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedEmployeeId":"%s"}
                                """.formatted(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        mockMvc.perform(post("/api/v1/work-tasks/" + taskId + "/start")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.actualStart").isNotEmpty());

        mockMvc.perform(post("/api/v1/work-tasks/" + taskId + "/complete")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes":"Done"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualEnd").isNotEmpty());
    }

    @Test
    void lifecycleRejectsSkippedTransitionsAndKeepsStartAndCancelIdempotent() throws Exception {
        MvcResult created = createTask();
        String taskId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/start", taskId)
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TASK_TRANSITION"));

        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/complete", taskId)
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_NOT_IN_PROGRESS"));

        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/assign", taskId)
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedEmployeeId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());

        MvcResult started = mockMvc.perform(post("/api/v1/work-tasks/{taskId}/start", taskId)
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn();
        JsonNode startedBody = objectMapper.readTree(started.getResponse().getContentAsString());

        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/start", taskId)
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(startedBody.path("version").asLong()));

        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/assign", taskId)
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedEmployeeId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TASK_TRANSITION"));

        MvcResult cancelled = mockMvc.perform(post("/api/v1/work-tasks/{taskId}/cancel", taskId)
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Weather made field work unsafe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.notes").value("Weather made field work unsafe"))
                .andExpect(jsonPath("$.actualEnd").isNotEmpty())
                .andReturn();
        long cancelledVersion = objectMapper.readTree(cancelled.getResponse().getContentAsString())
                .path("version").asLong();

        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/cancel", taskId)
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(cancelledVersion));

        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/complete", taskId)
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_CANCELLED"));
    }

    private MvcResult createTask() throws Exception {
        return mockMvc.perform(post("/api/v1/work-tasks")
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"WT-%d",
                                  "cropCycleId":"%s",
                                  "plotId":"%s",
                                  "taskType":"IRRIGATION",
                                  "title":"Morning irrigation",
                                  "priority":"HIGH"
                                }
                                """.formatted(System.nanoTime(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();
    }
}

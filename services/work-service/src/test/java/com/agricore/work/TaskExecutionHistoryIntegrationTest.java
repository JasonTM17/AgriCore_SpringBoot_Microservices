package com.agricore.work;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.farmaccess.FarmResourceAccess;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskExecutionHistoryIntegrationTest {

    private static final UUID FARM_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @BeforeEach
    void authorizePlotAccess() {
        when(farmAccessClient.requirePlot(any(UUID.class)))
                .thenAnswer(invocation -> new FarmResourceAccess(FARM_ID, invocation.getArgument(0)));
    }

    @Test
    void executionHistory_recordsCommittedLifecycleVersionsAndActors() throws Exception {
        CreatedTask task = createTask();
        assign(task.id(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/start", task.id())
                        .header("X-Dev-User", "field-worker-a")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/complete", task.id())
                        .header("X-Dev-User", "field-worker-a")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Inspected and completed\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/complete", task.id())
                        .header("X-Dev-User", "field-worker-a")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Inspected and completed\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/work-tasks/{taskId}/executions", task.id())
                        .header("X-Dev-User", "auditor-a")
                        .header("X-Dev-Roles", "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].action").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].previousStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].notes").value("Inspected and completed"))
                .andExpect(jsonPath("$.content[0].executedBy").value("field-worker-a"))
                .andExpect(jsonPath("$.content[0].taskVersion").value(3))
                .andExpect(jsonPath("$.content[1].action").value("STARTED"))
                .andExpect(jsonPath("$.content[1].previousStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$.content[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.content[1].executedBy").value("field-worker-a"))
                .andExpect(jsonPath("$.content[1].taskVersion").value(2));
    }

    @Test
    void cancellationHistory_isIdempotentFarmScopedAndPageBounded() throws Exception {
        CreatedTask task = createTask();

        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/cancel", task.id())
                        .header("X-Dev-User", "manager-a")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Unsafe field conditions\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/cancel", task.id())
                        .header("X-Dev-User", "manager-a")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/work-tasks/{taskId}/executions", task.id())
                        .header("X-Dev-User", "manager-a")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("CANCELLED"))
                .andExpect(jsonPath("$.content[0].previousStatus").value("CREATED"))
                .andExpect(jsonPath("$.content[0].notes").value("Unsafe field conditions"))
                .andExpect(jsonPath("$.content[0].executedBy").value("manager-a"))
                .andExpect(jsonPath("$.content[0].taskVersion").value(1));

        doThrow(new FarmAccessException("FARM_RESOURCE_NOT_FOUND", "Farm resource not found", 404))
                .when(farmAccessClient).requirePlot(task.plotId());
        mockMvc.perform(get("/api/v1/work-tasks/{taskId}/executions", task.id())
                        .header("X-Dev-User", "outsider")
                        .header("X-Dev-Roles", "AUDITOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/work-tasks/{taskId}/executions", UUID.randomUUID())
                        .queryParam("size", "101")
                        .header("X-Dev-User", "auditor-a")
                        .header("X-Dev-Roles", "AUDITOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private CreatedTask createTask() throws Exception {
        UUID plotId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/v1/work-tasks")
                        .header("X-Dev-User", "manager-a")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"EXECUTION-%d",
                                  "cropCycleId":"%s",
                                  "plotId":"%s",
                                  "taskType":"INSPECTION",
                                  "title":"Inspect crop condition",
                                  "priority":"MEDIUM"
                                }
                                """.formatted(System.nanoTime(), UUID.randomUUID(), plotId)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        return new CreatedTask(taskId, plotId);
    }

    private void assign(String taskId, UUID employeeId) throws Exception {
        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/assign", taskId)
                        .header("X-Dev-User", "manager-a")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedEmployeeId":"%s"}
                                """.formatted(employeeId)))
                .andExpect(status().isOk());
    }

    private record CreatedTask(String id, UUID plotId) {
    }
}

package com.agricore.work;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.farmaccess.FarmResourceAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
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
class WorkAssignmentHistoryIntegrationTest {

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
    void assignmentHistory_isImmutableOrderedAndIdempotent() throws Exception {
        CreatedTask task = createTask();
        UUID firstEmployee = UUID.randomUUID();
        UUID secondEmployee = UUID.randomUUID();

        assign(task.id(), firstEmployee, "manager-a", "FARM_MANAGER");
        assign(task.id(), firstEmployee, "manager-a", "FARM_MANAGER");
        assign(task.id(), secondEmployee, "agronomist-b", "AGRONOMIST");

        mockMvc.perform(get("/api/v1/work-tasks/{taskId}/assignments", task.id())
                        .header("X-Dev-User", "field-worker-a")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].workTaskId").value(task.id()))
                .andExpect(jsonPath("$.content[0].employeeId").value(secondEmployee.toString()))
                .andExpect(jsonPath("$.content[0].assignedBy").value("agronomist-b"))
                .andExpect(jsonPath("$.content[0].taskVersion").value(2))
                .andExpect(jsonPath("$.content[1].employeeId").value(firstEmployee.toString()))
                .andExpect(jsonPath("$.content[1].assignedBy").value("manager-a"))
                .andExpect(jsonPath("$.content[1].taskVersion").value(1));
    }

    @Test
    void assignmentHistory_masksInaccessibleTasksAndValidatesPagination() throws Exception {
        CreatedTask task = createTask();
        assign(task.id(), UUID.randomUUID(), "manager-a", "FARM_MANAGER");
        doThrow(new FarmAccessException("FARM_RESOURCE_NOT_FOUND", "Farm resource not found", 404))
                .when(farmAccessClient).requirePlot(task.plotId());

        mockMvc.perform(get("/api/v1/work-tasks/{taskId}/assignments", task.id())
                        .header("X-Dev-User", "outsider")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/work-tasks/{taskId}/assignments", UUID.randomUUID())
                        .queryParam("size", "0")
                        .header("X-Dev-User", "field-worker-a")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
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
                                  "code":"ASSIGNMENT-%d",
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

    private void assign(String taskId, UUID employeeId, String actor, String role) throws Exception {
        mockMvc.perform(post("/api/v1/work-tasks/{taskId}/assign", taskId)
                        .header("X-Dev-User", actor)
                        .header("X-Dev-Roles", role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedEmployeeId":"%s"}
                                """.formatted(employeeId)))
                .andExpect(status().isOk());
    }

    private record CreatedTask(String id, UUID plotId) {
    }
}

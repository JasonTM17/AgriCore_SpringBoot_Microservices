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
    void createAssignComplete_flow() throws Exception {
        String code = "WT-" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/api/v1/work-tasks")
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "cropCycleId":"%s",
                                  "plotId":"%s",
                                  "taskType":"IRRIGATION",
                                  "title":"Morning irrigation",
                                  "priority":"HIGH"
                                }
                                """.formatted(code, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();

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

        mockMvc.perform(post("/api/v1/work-tasks/" + taskId + "/complete")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes":"Done"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}

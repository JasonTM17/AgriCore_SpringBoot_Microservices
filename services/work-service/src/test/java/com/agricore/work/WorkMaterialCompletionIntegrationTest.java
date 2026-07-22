package com.agricore.work;

import com.agricore.common.event.EventTypes;
import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.work.infrastructure.client.InventoryStockClient;
import com.agricore.work.infrastructure.client.InventoryStockClientException;
import com.agricore.work.infrastructure.persistence.MaterialUsageJpaRepository;
import com.agricore.work.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.work.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkMaterialCompletionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MaterialUsageJpaRepository materialUsageRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @MockitoBean
    private FarmAccessClient farmAccessClient;
    @MockitoBean
    private InventoryStockClient inventoryStockClient;

    @Test
    void successfulMaterialCompletionIsIdempotentAndEmitsOneEventPerFact() throws Exception {
        String taskId = createTask();
        UUID inventoryItemId = UUID.randomUUID();
        when(inventoryStockClient.stockOut(eq(inventoryItemId), any(BigDecimal.class), anyString()))
                .thenReturn(new InventoryStockClient.StockOutResult(inventoryItemId, "KG"));
        String body = completionBody(inventoryItemId, "2.500");

        mockMvc.perform(authenticated(post("/api/v1/work-tasks/{taskId}/complete", taskId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.materials[0].inventoryItemId").value(inventoryItemId.toString()))
                .andExpect(jsonPath("$.materials[0].quantity").value(2.5))
                .andExpect(jsonPath("$.materials[0].unit").value("KG"))
                .andExpect(jsonPath("$.materials[0].status").value("CONSUMED"));

        mockMvc.perform(authenticated(post("/api/v1/work-tasks/{taskId}/complete", taskId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(inventoryStockClient).stockOut(eq(inventoryItemId), eq(new BigDecimal("2.500")), anyString());
        assertTaskEventCount(taskId, EventTypes.MATERIAL_CONSUMED, 1);
        assertTaskEventCount(taskId, EventTypes.WORK_TASK_COMPLETED, 1);
    }

    @Test
    void partialFailurePersistsProgressAndRetryDoesNotConsumeSuccessfulItemAgain() throws Exception {
        String taskId = createTask();
        UUID firstItem = UUID.randomUUID();
        UUID secondItem = UUID.randomUUID();
        when(inventoryStockClient.stockOut(eq(firstItem), any(BigDecimal.class), anyString()))
                .thenReturn(new InventoryStockClient.StockOutResult(firstItem, "L"));
        AtomicInteger secondAttempts = new AtomicInteger();
        when(inventoryStockClient.stockOut(eq(secondItem), any(BigDecimal.class), anyString()))
                .thenAnswer(ignored -> {
                    if (secondAttempts.getAndIncrement() == 0) {
                        throw InventoryStockClientException.downstream(409);
                    }
                    return new InventoryStockClient.StockOutResult(secondItem, "KG");
                });
        String body = """
                {
                  "notes":"Applied materials",
                  "materials":[
                    {"inventoryItemId":"%s","quantity":1.250},
                    {"inventoryItemId":"%s","quantity":3.000}
                  ]
                }
                """.formatted(firstItem, secondItem);

        mockMvc.perform(authenticated(post("/api/v1/work-tasks/{taskId}/complete", taskId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MATERIAL_CONSUMPTION_PENDING"));

        mockMvc.perform(authenticated(get("/api/v1/work-tasks/{taskId}", taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        var usagesAfterFailure = materialUsageRepository
                .findByWorkTaskIdOrderByCreatedAtAsc(UUID.fromString(taskId));
        assertThat(usagesAfterFailure).filteredOn(usage -> firstItem.equals(usage.getInventoryItemId()))
                .singleElement().extracting(usage -> usage.getStatus().name()).isEqualTo("CONSUMED");
        assertThat(usagesAfterFailure).filteredOn(usage -> secondItem.equals(usage.getInventoryItemId()))
                .singleElement().extracting(usage -> usage.getStatus().name()).isEqualTo("FAILED");

        mockMvc.perform(authenticated(post("/api/v1/work-tasks/{taskId}/complete", taskId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(inventoryStockClient, times(1)).stockOut(eq(firstItem), any(BigDecimal.class), anyString());
        ArgumentCaptor<String> referenceCaptor = ArgumentCaptor.forClass(String.class);
        verify(inventoryStockClient, times(2)).stockOut(
                eq(secondItem), eq(new BigDecimal("3.000")), referenceCaptor.capture()
        );
        assertThat(referenceCaptor.getAllValues()).hasSize(2).allMatch(referenceCaptor.getValue()::equals);
        assertTaskEventCount(taskId, EventTypes.MATERIAL_CONSUMED, 2);
        assertTaskEventCount(taskId, EventTypes.WORK_TASK_COMPLETED, 1);
    }

    @Test
    void duplicateItemsAndChangedRetryPayloadAreRejectedBeforeAdditionalStockCalls() throws Exception {
        String duplicateTaskId = createTask();
        UUID duplicatedItem = UUID.randomUUID();
        mockMvc.perform(authenticated(post("/api/v1/work-tasks/{taskId}/complete", duplicateTaskId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materials":[
                                    {"inventoryItemId":"%s","quantity":1.000},
                                    {"inventoryItemId":"%s","quantity":2.000}
                                  ]
                                }
                                """.formatted(duplicatedItem, duplicatedItem)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_MATERIAL_ITEM"));
        verify(inventoryStockClient, never()).stockOut(any(), any(), anyString());

        String retryTaskId = createTask();
        UUID retryItem = UUID.randomUUID();
        when(inventoryStockClient.stockOut(eq(retryItem), any(BigDecimal.class), anyString()))
                .thenThrow(InventoryStockClientException.downstream(409));
        mockMvc.perform(authenticated(post("/api/v1/work-tasks/{taskId}/complete", retryTaskId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody(retryItem, "1.000")))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(authenticated(post("/api/v1/work-tasks/{taskId}/complete", retryTaskId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody(retryItem, "2.000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_REQUEST_CHANGED"));

        mockMvc.perform(authenticated(post("/api/v1/work-tasks/{taskId}/complete", retryTaskId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_REQUEST_CHANGED"));
        verify(inventoryStockClient, times(1)).stockOut(eq(retryItem), any(BigDecimal.class), anyString());
    }

    @Test
    void invalidMaterialQuantityIsRejectedBeforeTaskLookup() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/work-tasks/{taskId}/complete", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody(UUID.randomUUID(), "1.0009")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(inventoryStockClient, never()).stockOut(any(), any(), anyString());
    }

    private String createTask() throws Exception {
        MvcResult result = mockMvc.perform(authenticated(post("/api/v1/work-tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"MATERIAL-%s",
                                  "cropCycleId":"%s",
                                  "plotId":"%s",
                                  "taskType":"FERTILIZING",
                                  "title":"Apply fertilizer",
                                  "priority":"HIGH"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private static String completionBody(UUID inventoryItemId, String quantity) {
        return """
                {
                  "notes":"Completed with materials",
                  "materials":[{"inventoryItemId":"%s","quantity":%s}]
                }
                """.formatted(inventoryItemId, quantity);
    }

    private void assertTaskEventCount(String taskId, String eventType, int expected) throws Exception {
        List<OutboxEventEntity> events = outboxRepository.findAll().stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .filter(event -> payloadTaskId(event).equals(taskId))
                .toList();
        assertThat(events).hasSize(expected);
        for (OutboxEventEntity event : events) {
            assertThat(objectMapper.readTree(event.getPayload()).path("eventId").asText())
                    .isEqualTo(event.getId().toString());
        }
    }

    private String payloadTaskId(OutboxEventEntity event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload()).path("payload");
            return payload.path("taskId").asText();
        } catch (Exception exception) {
            throw new AssertionError("Invalid outbox event JSON", exception);
        }
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request
                .header("X-Dev-User", "worker")
                .header("X-Dev-Roles", "FARM_MANAGER");
    }
}

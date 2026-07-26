package com.agricore.notification;

import com.agricore.notification.infrastructure.persistence.InAppDeliveryJpaRepository;
import com.agricore.notification.infrastructure.persistence.NotificationJpaRepository;
import com.agricore.notification.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.notification.infrastructure.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "agricore.notification.delivery.provider=smtp")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InAppNotificationInboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InAppDeliveryJpaRepository inAppRepository;
    @Autowired
    private NotificationJpaRepository notificationRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void inAppDeliveryCreatesQueryableIdempotentInboxEntryAndCanBeMarkedRead() throws Exception {
        String response = mockMvc.perform(post("/api/v1/notifications")
                        .header("X-Dev-User", "system-admin")
                        .header("X-Dev-Roles", "SYSTEM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channel":"IN_APP",
                                  "recipient":"operations",
                                  "subject":"Irrigation alert",
                                  "body":"Plot moisture is below the configured threshold.",
                                  "correlationId":"corr-inbox-1",
                                  "idempotencyKey":"inbox-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.deliveryAttempts").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String notificationId = objectMapper.readTree(response).path("id").asText();
        assertThat(inAppRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/notifications/in-app")
                        .header("X-Dev-User", "system-admin")
                        .header("X-Dev-Roles", "SYSTEM_ADMIN")
                        .param("recipient", "operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].notificationId").value(notificationId))
                .andExpect(jsonPath("$.content[0].subject").value("Irrigation alert"))
                .andExpect(jsonPath("$.content[0].readAt").doesNotExist());

        mockMvc.perform(patch("/api/v1/notifications/in-app/{notificationId}/read", notificationId)
                        .header("X-Dev-User", "system-admin")
                        .header("X-Dev-Roles", "SYSTEM_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(notificationId))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/notifications")
                        .header("X-Dev-User", "system-admin")
                        .header("X-Dev-Roles", "SYSTEM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channel":"IN_APP",
                                  "recipient":"operations",
                                  "subject":"Irrigation alert",
                                  "body":"Plot moisture is below the configured threshold.",
                                  "correlationId":"corr-inbox-1",
                                  "idempotencyKey":"inbox-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notificationId));

        assertThat(inAppRepository.count()).isEqualTo(1);
    }

    @Test
    void inboxRequiresNotificationAdministratorPermission() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/in-app"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/notifications/in-app")
                        .header("X-Dev-User", "farm-manager")
                        .header("X-Dev-Roles", "FARM_MANAGER"))
                .andExpect(status().isForbidden());
    }

    private void cleanDatabase() {
        inAppRepository.deleteAll();
        outboxRepository.deleteAll();
        processedEventRepository.deleteAll();
        notificationRepository.deleteAll();
    }
}

package com.agricore.notification;

import com.agricore.notification.application.port.NotificationDeliveryResult;
import com.agricore.notification.infrastructure.delivery.TestNotificationDeliveryAdapter;
import com.agricore.notification.infrastructure.persistence.NotificationJpaRepository;
import com.agricore.notification.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.notification.infrastructure.persistence.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestNotificationDeliveryAdapter deliveryAdapter;
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
        outboxRepository.deleteAll();
        processedEventRepository.deleteAll();
        notificationRepository.deleteAll();
        deliveryAdapter.reset();
    }

    @Test
    void realAdapterResultMarksNotificationSentWithoutLoggingMessagePii(CapturedOutput output) throws Exception {
        systemSend(request("corr-1", "delivery-1", "Batch HB-1 ready"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.deliveryAttempts").value(1));

        assertThat(deliveryAdapter.attempts()).isEqualTo(1);
        assertThat(outboxRepository.findAll()).extracting(event -> event.getEventType())
                .containsExactlyInAnyOrder("NotificationRequested.v2", "NotificationSent.v2");
        assertThat(output).doesNotContain("manager@agricore.local", "Batch HB-1 ready");
    }

    @Test
    void permanentAdapterFailureMarksNotificationFailedWithoutRetry() throws Exception {
        deliveryAdapter.respondWith(NotificationDeliveryResult.failed(
                "INVALID_RECIPIENT", "Recipient address is invalid", false));

        systemSend(request("corr-failed", "delivery-failed", "Failure case"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_RECIPIENT"))
                .andExpect(jsonPath("$.failureRetryable").value(false))
                .andExpect(jsonPath("$.failedAt").isNotEmpty())
                .andExpect(jsonPath("$.deliveryAttempts").value(1));

        assertThat(deliveryAdapter.attempts()).isEqualTo(1);
        assertThat(outboxRepository.findAll()).extracting(event -> event.getEventType())
                .containsExactlyInAnyOrder("NotificationRequested.v2", "NotificationFailed.v2");
    }

    @Test
    void retryableFailureUsesBoundedAttemptsAndCanRecover() throws Exception {
        NotificationDeliveryResult transientFailure = NotificationDeliveryResult.failed(
                "SMTP_DELIVERY_FAILED", "SMTP delivery failed", true);
        deliveryAdapter.respondWith(transientFailure, transientFailure, NotificationDeliveryResult.sent());

        systemSend(request("corr-retry", "delivery-retry", "Retry case"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.deliveryAttempts").value(3));

        assertThat(deliveryAdapter.attempts()).isEqualTo(3);
    }

    @Test
    void idempotencyKeyPreventsDuplicateDeliveryAndRejectsChangedIntent() throws Exception {
        String request = request("corr-idem", "delivery-idem", "Stable body");
        String firstId = responseId(systemSend(request).andExpect(status().isCreated()));
        String secondId = responseId(systemSend(request).andExpect(status().isCreated()));

        assertThat(secondId).isEqualTo(firstId);
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(deliveryAdapter.attempts()).isEqualTo(1);

        systemSend(request("corr-idem", "delivery-idem", "Changed body"))
                .andExpect(status().isConflict());
    }

    @Test
    void omittedIdempotencyKeyCreatesIndependentDeliveries() throws Exception {
        systemSend(request("corr-no-key", null, "First delivery")).andExpect(status().isCreated());
        systemSend(request("corr-no-key", null, "First delivery")).andExpect(status().isCreated());

        assertThat(notificationRepository.count()).isEqualTo(2);
        assertThat(deliveryAdapter.attempts()).isEqualTo(2);
    }

    @Test
    void directDeliveryRequiresSystemAdministratorAndKnownChannel() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("corr-auth", "delivery-auth", "Auth case")))
                .andExpect(status().isUnauthorized());
        roleSend("FARM_MANAGER", request("corr-auth", "delivery-auth", "Auth case"))
                .andExpect(status().isForbidden());
        systemSend(request("corr-auth", "delivery-auth", "Auth case").replace("EMAIL", "FAX"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions systemSend(String body) throws Exception {
        return roleSend("SYSTEM_ADMIN", body);
    }

    private ResultActions roleSend(String role, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/notifications")
                .header("X-Dev-User", role.toLowerCase())
                .header("X-Dev-Roles", role)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String responseId(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .path("id").asText();
    }

    private static String request(String correlationId, String idempotencyKey, String body) {
        String idempotencyProperty = idempotencyKey == null
                ? ""
                : ",\n  \"idempotencyKey\":\"" + idempotencyKey + "\"";
        return """
                {
                  "channel":"EMAIL",
                  "recipient":"manager@agricore.local",
                  "subject":"Harvest completed",
                  "body":"%s",
                  "correlationId":"%s"%s
                }
                """.formatted(body, correlationId, idempotencyProperty);
    }
}

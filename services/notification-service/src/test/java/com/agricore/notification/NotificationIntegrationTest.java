package com.agricore.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sendNotification() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .header("X-Dev-User", "system")
                        .header("X-Dev-Roles", "SYSTEM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channel":"EMAIL",
                                  "recipient":"manager@agricore.local",
                                  "subject":"Harvest completed",
                                  "body":"Batch HB-1 ready",
                                  "correlationId":"corr-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.channel").value("EMAIL"));
    }

    @Test
    void rejectsUnauthenticatedNotificationRequest() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRoleWithoutNotificationPermission() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .header("X-Dev-User", "field-worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsFarmManagerDirectDelivery() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .header("X-Dev-User", "farm-manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnknownDeliveryChannel() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .header("X-Dev-User", "system")
                        .header("X-Dev-Roles", "SYSTEM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("EMAIL", "FAX")))
                .andExpect(status().isBadRequest());
    }

    private static String validRequest() {
        return """
                {
                  "channel":"EMAIL",
                  "recipient":"manager@agricore.local",
                  "subject":"Harvest completed",
                  "body":"Batch HB-1 ready",
                  "correlationId":"corr-auth"
                }
                """;
    }
}

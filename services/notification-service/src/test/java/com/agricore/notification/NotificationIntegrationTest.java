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

    /**
     * The caller picks the recipient and the whole message body, so this endpoint must not be open
     * to every authenticated token — otherwise the lowest-privilege role can address arbitrary
     * people once a real email adapter replaces the log sink.
     */
    @Test
    void sendNotification_isRefusedForNonAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channel":"EMAIL",
                                  "recipient":"victim@example.com",
                                  "subject":"Phish",
                                  "body":"Click here",
                                  "correlationId":"corr-2"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * The request record carries {@code @NotBlank} and {@code @Size}, and the controller carries
     * {@code @Valid}, but with no advice the rejection was rendered by Boot's default error
     * controller — a 400 with no {@code code} and no per-field detail. Clients cannot tell the
     * caller which field to fix from that.
     */
    @Test
    void invalidRequest_returnsFieldViolationsInPlatformContract() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .header("X-Dev-User", "system")
                        .header("X-Dev-Roles", "SYSTEM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channel":"",
                                  "recipient":"manager@agricore.local",
                                  "subject":"Harvest completed",
                                  "body":"Batch HB-1 ready"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.violations[?(@.field=='channel')]").exists());
    }
}

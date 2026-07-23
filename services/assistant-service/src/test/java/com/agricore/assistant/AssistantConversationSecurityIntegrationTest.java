package com.agricore.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssistantConversationSecurityIntegrationTest extends AssistantApiIntegrationTestSupport {

    private static final UUID OWNER = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void conversationEndpointsRequireAuthenticationAndUuidSubject() throws Exception {
        mockMvc.perform(get(CONVERSATIONS_PATH))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(CONVERSATIONS_PATH)
                        .header("X-Dev-User", "not-a-uuid")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACTOR_SUBJECT"))
                .andExpect(jsonPath("$.message").value("Authenticated subject is invalid"));
    }

    @Test
    void lifecycleIsAvailableToAnyAuthenticatedRole() throws Exception {
        createEnterpriseConversation(OWNER, "FIELD_WORKER", "Audit discussion");

        mockMvc.perform(authenticated(get(CONVERSATIONS_PATH), OWNER, "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void conversationEndpointsRequireAssistantPermissionEvenForAuthenticatedRoles() throws Exception {
        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH)
                                .header("X-Dev-Permissions", "")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title":"Permission denied","contextType":"ENTERPRISE"}
                                        """),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void requestBodyValidationRejectsBlankTitleAndMismatchedContext() throws Exception {
        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title":"   ","contextType":"ENTERPRISE"}
                                        """),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("title"))
                .andExpect(jsonPath("$.violations[0].rejectedValue").doesNotExist());

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title":"Invalid farm scope",
                                          "contextType":"ENTERPRISE",
                                          "farmId":"30000000-0000-0000-0000-000000000010"
                                        }
                                        """),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONVERSATION_CONTEXT"));
    }

    @Test
    void malformedEnumsIdentifiersAndPageBoundsReturnStableBadRequestErrors() throws Exception {
        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title":"Bad enum","contextType":"UNKNOWN"}
                                        """),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));

        mockMvc.perform(authenticated(
                        get(CONVERSATIONS_PATH).queryParam("size", "101"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(authenticated(
                        get(CONVERSATIONS_PATH).queryParam("status", "UNKNOWN"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        mockMvc.perform(authenticated(
                        get(CONVERSATIONS_PATH + "/not-a-uuid"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void unsupportedRoutesAndMethodsAreNotMisreportedAsServerFailures() throws Exception {
        mockMvc.perform(authenticated(
                        get("/api/v1/assistant/not-a-resource"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(authenticated(
                        put(CONVERSATIONS_PATH),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}

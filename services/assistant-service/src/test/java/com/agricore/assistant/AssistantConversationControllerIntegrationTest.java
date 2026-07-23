package com.agricore.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssistantConversationControllerIntegrationTest extends AssistantApiIntegrationTestSupport {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void ownerCanCreateListReadAndIdempotentlyArchiveConversation() throws Exception {
        UUID conversationId = createEnterpriseConversation(
                OWNER,
                "FARM_MANAGER,AGRONOMIST",
                "  Season planning  "
        );

        mockMvc.perform(authenticated(get(CONVERSATIONS_PATH), OWNER, "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(conversationId.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Season planning"))
                .andExpect(jsonPath("$.content[0].roleSnapshot[0]").value("AGRONOMIST"))
                .andExpect(jsonPath("$.content[0].roleSnapshot[1]").value("FARM_MANAGER"));

        mockMvc.perform(authenticated(get(CONVERSATIONS_PATH + "/" + conversationId), OWNER, "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/archive"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty())
                .andExpect(jsonPath("$.purgeAfter").isNotEmpty());

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH + "/" + conversationId + "/archive"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(authenticated(get(CONVERSATIONS_PATH), OWNER, "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(authenticated(
                        get(CONVERSATIONS_PATH).queryParam("status", "ARCHIVED"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant_audit_events WHERE conversation_id = ?",
                Integer.class,
                conversationId
        );
        org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(2);
    }

    @Test
    void inaccessibleAndMissingConversationsShareTheSameNotFoundContract() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "Private conversation");
        UUID missingId = UUID.fromString("10000000-0000-0000-0000-000000000099");

        assertNotFound(get(CONVERSATIONS_PATH + "/" + conversationId), OTHER_OWNER);
        assertNotFound(post(CONVERSATIONS_PATH + "/" + conversationId + "/archive"), OTHER_OWNER);
        assertNotFound(get(CONVERSATIONS_PATH + "/" + conversationId + "/messages"), OTHER_OWNER);
        assertNotFound(get(CONVERSATIONS_PATH + "/" + missingId), OWNER);

        mockMvc.perform(authenticated(get(CONVERSATIONS_PATH + "/" + conversationId), OWNER, "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    private void assertNotFound(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            UUID subject
    ) throws Exception {
        mockMvc.perform(authenticated(request, subject, "FIELD_WORKER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Conversation not found"));
    }
}

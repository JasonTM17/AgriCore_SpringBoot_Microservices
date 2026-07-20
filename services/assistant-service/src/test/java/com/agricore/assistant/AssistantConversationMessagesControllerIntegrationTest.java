package com.agricore.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssistantConversationMessagesControllerIntegrationTest extends AssistantApiIntegrationTestSupport {

    private static final UUID OWNER = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void ownerReceivesChronologicalPaginatedMessageHistory() throws Exception {
        UUID conversationId = createEnterpriseConversation(OWNER, "FIELD_WORKER", "History");
        seedMessages(conversationId);

        mockMvc.perform(authenticated(
                        get(CONVERSATIONS_PATH + "/" + conversationId + "/messages")
                                .queryParam("page", "0")
                                .queryParam("size", "1"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].sequenceNo").value(0))
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.content[0].content").value("How is the crop?"));

        mockMvc.perform(authenticated(
                        get(CONVERSATIONS_PATH + "/" + conversationId + "/messages")
                                .queryParam("page", "1")
                                .queryParam("size", "1"),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.content[0].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.content[0].content").value("The crop is healthy."));
    }

    private void seedMessages(UUID conversationId) {
        UUID generationId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                        INSERT INTO chat_generations (
                            id, conversation_id, owner_user_id, idempotency_key, request_hash,
                            status, role_snapshot, provider, queued_at, created_at, updated_at
                        ) VALUES (?, ?, ?, 'history-test', ?, 'COMPLETED',
                                  '["FIELD_WORKER"]', 'test', ?, ?, ?)
                        """,
                generationId,
                conversationId,
                OWNER,
                "c".repeat(64),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update("""
                        INSERT INTO conversation_messages (
                            id, conversation_id, generation_id, sequence_no, role, content, created_at
                        ) VALUES (?, ?, ?, 0, 'USER', 'How is the crop?', ?)
                        """,
                UUID.randomUUID(), conversationId, generationId, Timestamp.from(now));
        jdbc.update("""
                        INSERT INTO conversation_messages (
                            id, conversation_id, generation_id, sequence_no, role, content, created_at
                        ) VALUES (?, ?, ?, 1, 'ASSISTANT', 'The crop is healthy.', ?)
                        """,
                UUID.randomUUID(), conversationId, generationId, Timestamp.from(now.plusMillis(1)));
        jdbc.update("UPDATE conversations SET next_message_sequence = 2 WHERE id = ?", conversationId);
    }
}

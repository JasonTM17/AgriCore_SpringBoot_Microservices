package com.agricore.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AssistantApiIntegrationTestSupport {

    static final String CONVERSATIONS_PATH = "/api/v1/assistant/conversations";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void cleanAssistantData() {
        jdbc.update("DELETE FROM generation_events");
        jdbc.update("DELETE FROM conversation_messages");
        jdbc.update("DELETE FROM chat_generations");
        jdbc.update("DELETE FROM assistant_audit_events");
        jdbc.update("DELETE FROM conversations");
    }

    MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request,
            UUID subject,
            String roles
    ) {
        return request
                .header("X-Dev-User", subject.toString())
                .header("X-Dev-Roles", roles);
    }

    UUID createEnterpriseConversation(UUID subject, String roles, String title) throws Exception {
        MvcResult result = mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "%s",
                                          "contextType": "ENTERPRISE"
                                        }
                                        """.formatted(title)),
                        subject,
                        roles
                ))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}

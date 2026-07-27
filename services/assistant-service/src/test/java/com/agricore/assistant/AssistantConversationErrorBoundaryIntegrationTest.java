package com.agricore.assistant;

import com.agricore.assistant.application.service.ConversationApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssistantConversationErrorBoundaryIntegrationTest extends AssistantApiIntegrationTestSupport {

    private static final UUID OWNER = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @MockitoBean
    private ConversationApplicationService service;

    @Test
    void unexpectedFailureReturnsStableErrorWithoutLeakingInternalDetail() throws Exception {
        when(service.list(any(), isNull(), any()))
                .thenThrow(new IllegalStateException("provider-secret-value"));

        mockMvc.perform(authenticated(get(CONVERSATIONS_PATH), OWNER, "FIELD_WORKER"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("provider-secret-value"))));
    }

    @Test
    void unsupportedAcceptReturnsNotAcceptableApiErrorInsteadOfGenericServerFailure() throws Exception {
        mockMvc.perform(authenticated(
                        get(CONVERSATIONS_PATH
                                + "/50000000-0000-0000-0000-000000000002/generations/"
                                + "50000000-0000-0000-0000-000000000003/events")
                                .accept(MediaType.APPLICATION_XML),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"))
                .andExpect(jsonPath("$.message").value("Accept header is not supported"));
    }
}

package com.agricore.assistant;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssistantConversationFarmAccessIntegrationTest extends AssistantApiIntegrationTestSupport {

    private static final UUID OWNER = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void deniedFarmAccessReturnsStructuredErrorWithoutWritingConversationOrAudit() throws Exception {
        UUID farmId = UUID.fromString("40000000-0000-0000-0000-000000000010");
        doThrow(new FarmAccessException("FARM_ACCESS_DENIED", "Farm access denied", 403))
                .when(farmAccessClient).requireFarm(farmId);

        mockMvc.perform(authenticated(
                        post(CONVERSATIONS_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title":"Denied farm",
                                          "contextType":"FARM",
                                          "farmId":"%s"
                                        }
                                        """.formatted(farmId)),
                        OWNER,
                        "FIELD_WORKER"
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Farm access denied"));

        verify(farmAccessClient).requireFarm(farmId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assistant_audit_events", Integer.class)).isZero();
    }
}

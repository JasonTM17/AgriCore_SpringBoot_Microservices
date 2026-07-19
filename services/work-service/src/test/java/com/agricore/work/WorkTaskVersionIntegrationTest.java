package com.agricore.work;

import com.agricore.farmaccess.FarmAccessClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkTaskVersionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void mutationResponses_returnCommittedVersions() throws Exception {
        MvcResult created = mockMvc.perform(authenticated(post("/api/v1/work-tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdBody = readBody(created);
        String taskId = createdBody.required("id").asText();
        long createdVersion = createdBody.required("version").asLong();

        MvcResult assigned = mockMvc.perform(authenticated(post("/api/v1/work-tasks/" + taskId + "/assign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedEmployeeId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andReturn();
        long assignedVersion = readBody(assigned).required("version").asLong();
        long assignedReloadVersion = getVersion(taskId);

        MvcResult completed = mockMvc.perform(authenticated(post("/api/v1/work-tasks/" + taskId + "/complete"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes":"Hoàn thành tại hiện trường"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long completedVersion = readBody(completed).required("version").asLong();
        long completedReloadVersion = getVersion(taskId);

        assertThat(assignedVersion).isEqualTo(createdVersion + 1);
        assertThat(assignedReloadVersion).isEqualTo(assignedVersion);
        assertThat(completedVersion).isEqualTo(assignedVersion + 1);
        assertThat(completedReloadVersion).isEqualTo(completedVersion);
    }

    private long getVersion(String taskId) throws Exception {
        MvcResult result = mockMvc.perform(authenticated(get("/api/v1/work-tasks/" + taskId)))
                .andExpect(status().isOk())
                .andReturn();
        return readBody(result).required("version").asLong();
    }

    private String createRequest() {
        return """
                {
                  "code":"VERSION-%d",
                  "cropCycleId":"%s",
                  "plotId":"%s",
                  "taskType":"IRRIGATION",
                  "title":"Kiểm tra phiên bản",
                  "priority":"HIGH"
                }
                """.formatted(System.nanoTime(), UUID.randomUUID(), UUID.randomUUID());
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request
                .header("X-Dev-User", "agronomist")
                .header("X-Dev-Roles", "AGRONOMIST");
    }
}

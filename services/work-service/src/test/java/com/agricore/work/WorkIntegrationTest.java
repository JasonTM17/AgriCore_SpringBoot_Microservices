package com.agricore.work;

import com.agricore.farmaccess.FarmAccessClient;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void createAssignComplete_flow() throws Exception {
        String code = "WT-" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/api/v1/work-tasks")
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "cropCycleId":"%s",
                                  "plotId":"%s",
                                  "taskType":"IRRIGATION",
                                  "title":"Morning irrigation",
                                  "priority":"HIGH"
                                }
                                """.formatted(code, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();

        String taskId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        UUID employee = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/work-tasks/" + taskId + "/assign")
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedEmployeeId":"%s"}
                                """.formatted(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        mockMvc.perform(post("/api/v1/work-tasks/" + taskId + "/complete")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notes":"Done"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}

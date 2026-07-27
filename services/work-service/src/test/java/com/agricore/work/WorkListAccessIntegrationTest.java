package com.agricore.work;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkListAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void list_withoutPlotScope_requiresSystemAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/work-tasks")
                        .queryParam("cropCycleId", UUID.randomUUID().toString())
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FARM_SCOPE_REQUIRED"));

        verify(farmAccessClient).isSystemAdmin();
    }

    @Test
    void list_withoutPlotScope_allowsSystemAdminGlobalView() throws Exception {
        when(farmAccessClient.isSystemAdmin()).thenReturn(true);

        mockMvc.perform(get("/api/v1/work-tasks")
                        .queryParam("cropCycleId", UUID.randomUUID().toString())
                        .header("X-Dev-User", "admin")
                        .header("X-Dev-Roles", "SYSTEM_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(farmAccessClient).isSystemAdmin();
    }

    @Test
    void list_withPlotScope_usesAuthoritativePlotLookup() throws Exception {
        UUID plotId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/work-tasks")
                        .queryParam("plotId", plotId.toString())
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk());

        verify(farmAccessClient).requirePlot(plotId);
    }

    @Test
    void list_withCycleAndPlot_appliesBothFilters() throws Exception {
        UUID cropCycleId = UUID.randomUUID();
        UUID requestedPlotId = UUID.randomUUID();
        UUID otherPlotId = UUID.randomUUID();
        createTask(cropCycleId, requestedPlotId, "2026-01-01");
        createTask(cropCycleId, otherPlotId, "2026-01-02");
        clearInvocations(farmAccessClient);

        mockMvc.perform(get("/api/v1/work-tasks")
                        .queryParam("cropCycleId", cropCycleId.toString())
                        .queryParam("plotId", requestedPlotId.toString())
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].plotId").value(requestedPlotId.toString()));

        verify(farmAccessClient).requirePlot(requestedPlotId);
    }

    @Test
    void list_whenPlotIsNotVisible_masksNotFoundAndLeaksNoTaskData() throws Exception {
        UUID plotId = UUID.randomUUID();
        String protectedCode = createTask(UUID.randomUUID(), plotId, "2026-02-01");
        clearInvocations(farmAccessClient);
        doThrow(new FarmAccessException(
                "FARM_RESOURCE_NOT_FOUND",
                "Farm resource not found",
                HttpStatus.NOT_FOUND.value()
        )).when(farmAccessClient).requirePlot(plotId);

        String body = mockMvc.perform(get("/api/v1/work-tasks")
                        .queryParam("plotId", plotId.toString())
                        .header("X-Dev-User", "other-farm-user")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FARM_RESOURCE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(protectedCode);
        verify(farmAccessClient).requirePlot(plotId);
    }

    @Test
    void unauthenticatedList_doesNotReachFarmAccessBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/work-tasks"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(farmAccessClient);
    }

    private String createTask(UUID cropCycleId, UUID plotId, String date) throws Exception {
        String code = "LIST-" + System.nanoTime();
        mockMvc.perform(post("/api/v1/work-tasks")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","cropCycleId":"%s","plotId":"%s",
                                 "taskType":"IRRIGATION","title":"List task","priority":"HIGH",
                                 "scheduledStart":"%sT08:00:00Z"}
                                """.formatted(code, cropCycleId, plotId, date)))
                .andExpect(status().isCreated());
        return code;
    }
}

package com.agricore.cropcycle;

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
class CropCycleListAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void listRequiresCanonicalReadPermissionEvenWhenRoleMatches() throws Exception {
        UUID farmId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/crop-cycles")
                        .queryParam("farmId", farmId.toString())
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .header("X-Dev-Permissions", ""))
                .andExpect(status().isForbidden());

        verifyNoInteractions(farmAccessClient);
    }

    @Test
    void list_withoutScope_requiresSystemAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/crop-cycles")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FARM_SCOPE_REQUIRED"));

        verify(farmAccessClient).isSystemAdmin();
    }

    @Test
    void list_withoutScope_allowsSystemAdminGlobalView() throws Exception {
        when(farmAccessClient.isSystemAdmin()).thenReturn(true);

        mockMvc.perform(get("/api/v1/crop-cycles")
                        .header("X-Dev-User", "admin")
                        .header("X-Dev-Roles", "SYSTEM_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(farmAccessClient).isSystemAdmin();
    }

    @Test
    void list_withFarmAndPlot_requiresAuthoritativePairAccess() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/crop-cycles")
                        .queryParam("farmId", farmId.toString())
                        .queryParam("plotId", plotId.toString())
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk());

        verify(farmAccessClient).requireFarmPlot(farmId, plotId);
    }

    @Test
    void list_withFarmAndPlot_appliesBothFilters() throws Exception {
        UUID requestedFarmId = UUID.randomUUID();
        UUID otherFarmId = UUID.randomUUID();
        UUID sharedPlotId = UUID.randomUUID();
        createCycle(requestedFarmId, sharedPlotId, "2026-01-01", "2026-02-28");
        createCycle(otherFarmId, sharedPlotId, "2026-03-01", "2026-04-30");
        clearInvocations(farmAccessClient);

        mockMvc.perform(get("/api/v1/crop-cycles")
                        .queryParam("farmId", requestedFarmId.toString())
                        .queryParam("plotId", sharedPlotId.toString())
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].farmId").value(requestedFarmId.toString()));

        verify(farmAccessClient).requireFarmPlot(requestedFarmId, sharedPlotId);
    }

    @Test
    void list_whenFarmIsNotVisible_masksNotFoundAndLeaksNoCycleData() throws Exception {
        UUID farmId = UUID.randomUUID();
        String protectedCode = createCycle(
                farmId,
                UUID.randomUUID(),
                "2026-05-01",
                "2026-06-30"
        );
        clearInvocations(farmAccessClient);
        doThrow(new FarmAccessException(
                "FARM_RESOURCE_NOT_FOUND",
                "Farm resource not found",
                HttpStatus.NOT_FOUND.value()
        )).when(farmAccessClient).requireFarm(farmId);

        String body = mockMvc.perform(get("/api/v1/crop-cycles")
                        .queryParam("farmId", farmId.toString())
                        .header("X-Dev-User", "other-farm-user")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FARM_RESOURCE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(protectedCode);
        verify(farmAccessClient).requireFarm(farmId);
    }

    @Test
    void list_withSingleScope_usesMatchingAuthoritativeLookup() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/crop-cycles")
                        .queryParam("farmId", farmId.toString())
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk());
        verify(farmAccessClient).requireFarm(farmId);

        mockMvc.perform(get("/api/v1/crop-cycles")
                        .queryParam("plotId", plotId.toString())
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isOk());
        verify(farmAccessClient).requirePlot(plotId);
    }

    @Test
    void unauthenticatedList_doesNotReachFarmAccessBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/crop-cycles"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(farmAccessClient);
    }

    private String createCycle(UUID farmId, UUID plotId, String startDate, String endDate) throws Exception {
        String code = "LIST-" + System.nanoTime();
        String body = """
                {"code":"%s","farmId":"%s","plotId":"%s","cropId":"%s",
                 "plannedStartDate":"%s","plannedEndDate":"%s"}
                """.formatted(code, farmId, plotId, UUID.randomUUID(), startDate, endDate);

        mockMvc.perform(post("/api/v1/crop-cycles")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        return code;
    }
}

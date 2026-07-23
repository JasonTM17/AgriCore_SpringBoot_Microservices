package com.agricore.cropcatalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CropCatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listSeededCrops() throws Exception {
        mockMvc.perform(get("/api/v1/crops")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.content[?(@.code=='RICE_ST25')]").exists());
    }

    @Test
    void getByCode() throws Exception {
        mockMvc.perform(get("/api/v1/crops/by-code/COFFEE_ROBUSTA")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cà phê Robusta"));
    }

    @Test
    void combinedCategoryAndNameFilters_areAppliedTogether() throws Exception {
        mockMvc.perform(get("/api/v1/crops")
                        .queryParam("category", "PERENNIAL")
                        .queryParam("q", "Cà phê")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("COFFEE_ROBUSTA"));
    }

    @Test
    void listVarieties_isCropScopedSearchableAndDeterministic() throws Exception {
        mockMvc.perform(get("/api/v1/crops/{cropId}/varieties",
                        "22222222-2222-2222-2222-222222222004")
                        .queryParam("q", "st")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id")
                        .value("33333333-3333-3333-3333-333333333003"))
                .andExpect(jsonPath("$.content[0].cropId")
                        .value("22222222-2222-2222-2222-222222222004"))
                .andExpect(jsonPath("$.content[0].code").value("ST25"))
                .andExpect(jsonPath("$.content[0].origin").value("Soc Trang"));
    }

    @Test
    void getVariety_returnsSeededDetail() throws Exception {
        mockMvc.perform(get("/api/v1/crop-varieties/{varietyId}",
                        "33333333-3333-3333-3333-333333333001")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TR4"))
                .andExpect(jsonPath("$.name").value("TR4 Robusta"))
                .andExpect(jsonPath("$.notes").value("High yield clone"));
    }

    @Test
    void varietyList_rejectsMissingCropAndInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/crops/{cropId}/varieties", UUID.randomUUID())
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/crops/{cropId}/varieties",
                        "22222222-2222-2222-2222-222222222001")
                        .queryParam("size", "0")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value(
                        "/api/v1/crops/22222222-2222-2222-2222-222222222001/varieties"));

        mockMvc.perform(get("/api/v1/crops")
                        .queryParam("page", "-1")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/v1/crops"));
    }

    @Test
    void careProfile_returnsGrowthDiseaseAndOrderedRecommendations() throws Exception {
        mockMvc.perform(get("/api/v1/crops/{cropId}/care-profile",
                        "22222222-2222-2222-2222-222222222004")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cropId")
                        .value("22222222-2222-2222-2222-222222222004"))
                .andExpect(jsonPath("$.growthRequirement.irrigationIntervalDaysMin").value(2))
                .andExpect(jsonPath("$.growthRequirement.fertilizationIntervalDaysMax").value(25))
                .andExpect(jsonPath("$.commonDiseases[0].code").value("RICE_BLAST"))
                .andExpect(jsonPath("$.recommendations.length()").value(2))
                .andExpect(jsonPath("$.recommendations[0].category").value("FERTILIZATION"))
                .andExpect(jsonPath("$.recommendations[1].category").value("PEST_MANAGEMENT"));
    }

    @Test
    void careProfile_rejectsMissingCropAndAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/v1/crops/{cropId}/care-profile", UUID.randomUUID())
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/crops/{cropId}/care-profile",
                        "22222222-2222-2222-2222-222222222004"))
                .andExpect(status().isUnauthorized());
    }
}

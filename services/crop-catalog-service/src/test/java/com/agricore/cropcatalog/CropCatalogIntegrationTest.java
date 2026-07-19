package com.agricore.cropcatalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
}

package com.agricore.cropcatalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
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

    /**
     * The status was always right; the body was not. Without an advice the service falls through
     * to Boot's default error controller, which emits no {@code code} and no {@code message}, so a
     * client written against the platform {@code ApiError} contract reads null from this service
     * alone.
     */
    @Test
    void unknownCropId_returnsPlatformErrorContract() throws Exception {
        mockMvc.perform(get("/api/v1/crops/" + UUID.randomUUID())
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Crop not found"))
                .andExpect(jsonPath("$.path").value(startsWith("/api/v1/crops/")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void unknownCropCode_returnsPlatformErrorContract() throws Exception {
        mockMvc.perform(get("/api/v1/crops/by-code/NOT_A_REAL_CROP")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}

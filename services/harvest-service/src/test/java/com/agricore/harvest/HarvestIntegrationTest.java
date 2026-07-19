package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HarvestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void completeHarvest_writesBatchAndEventId() throws Exception {
        String code = "HB-" + System.nanoTime();
        mockMvc.perform(post("/api/v1/harvests/complete")
                        .header("X-Dev-User", "mgr")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "cropCycleId":"%s",
                                  "plotId":"%s",
                                  "warehouseId":"%s",
                                  "productCode":"COFFEE-ROBUSTA",
                                  "grossWeightKg":3500,
                                  "netWeightKg":3300,
                                  "qualityGrade":"GRADE_A",
                                  "farmName":"Nong trai Dak Lak",
                                  "plotCode":"DL-A01",
                                  "productName":"Ca phe Robusta",
                                  "careSummary":"Drip irrigation"
                                }
                                """.formatted(code, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.netWeightKg").value(3300))
                .andExpect(jsonPath("$.lastOutboxEventId").isNotEmpty());
    }
}

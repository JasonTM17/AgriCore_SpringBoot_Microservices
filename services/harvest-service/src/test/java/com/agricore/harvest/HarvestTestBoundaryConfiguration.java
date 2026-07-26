package com.agricore.harvest;

import com.agricore.harvest.infrastructure.client.CropCycleAccessClient;
import com.agricore.harvest.infrastructure.client.WarehouseAccessClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
class HarvestTestBoundaryConfiguration {

    @Bean
    @Primary
    CropCycleAccessClient testCropCycleAccessClient() {
        return (cropCycleId, farmId, plotId) -> {
            // Integration tests focus on harvest persistence and use a deterministic authorized boundary.
        };
    }

    @Bean
    @Primary
    WarehouseAccessClient testWarehouseAccessClient() {
        return (warehouseId, farmId) -> {
            // Integration tests focus on harvest persistence and use a deterministic authorized boundary.
        };
    }
}

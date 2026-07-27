package com.agricore.inventory;

import com.agricore.inventory.infrastructure.messaging.OutboxRetryProperties;
import com.agricore.inventory.infrastructure.security.InventoryInternalSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        InventoryInternalSecurityProperties.class,
        OutboxRetryProperties.class
})
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}

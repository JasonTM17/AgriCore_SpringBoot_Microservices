package com.agricore.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.agricore.inventory.infrastructure.security.InventoryInternalSecurityProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(InventoryInternalSecurityProperties.class)
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}

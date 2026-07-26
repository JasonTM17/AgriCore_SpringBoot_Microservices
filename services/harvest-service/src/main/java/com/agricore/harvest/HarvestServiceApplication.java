package com.agricore.harvest;

import com.agricore.harvest.infrastructure.messaging.OutboxRetryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OutboxRetryProperties.class)
public class HarvestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HarvestServiceApplication.class, args);
    }
}

package com.agricore.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.agricore")
@ConfigurationPropertiesScan
public class AssistantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssistantServiceApplication.class, args);
    }
}

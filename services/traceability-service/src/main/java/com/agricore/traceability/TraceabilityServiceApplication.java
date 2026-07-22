package com.agricore.traceability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TraceabilityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TraceabilityServiceApplication.class, args);
    }
}

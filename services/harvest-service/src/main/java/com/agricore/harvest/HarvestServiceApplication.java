package com.agricore.harvest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HarvestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HarvestServiceApplication.class, args);
    }
}

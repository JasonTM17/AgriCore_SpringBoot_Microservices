package com.agricore.cropcycle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CropCycleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CropCycleServiceApplication.class, args);
    }
}

package com.agricore.harvest.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class HarvestMetrics {

    private final Timer successfulProcessing;
    private final Timer failedProcessing;

    public HarvestMetrics(MeterRegistry registry) {
        successfulProcessing = timer(registry, "success");
        failedProcessing = timer(registry, "failure");
    }

    public <T> T recordProcessing(Supplier<T> operation) {
        Timer.Sample sample = Timer.start();
        try {
            T result = operation.get();
            sample.stop(successfulProcessing);
            return result;
        } catch (RuntimeException exception) {
            sample.stop(failedProcessing);
            throw exception;
        }
    }

    private static Timer timer(MeterRegistry registry, String outcome) {
        return Timer.builder("agricore.harvest.processing")
                .description("Harvest lifecycle processing latency")
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry);
    }
}

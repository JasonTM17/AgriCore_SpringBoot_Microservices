package com.agricore.assistant.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantGenerationWorkerProperties.class)
public class AssistantGenerationWorkerConfiguration {

    @Bean(destroyMethod = "dispose")
    Scheduler assistantGenerationScheduler(AssistantGenerationWorkerProperties properties) {
        properties.validate();
        return Schedulers.newBoundedElastic(
                properties.getConcurrency(),
                properties.getQueueCapacity(),
                "assistant-generation"
        );
    }
}

package com.agricore.assistant.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantGenerationEventStreamProperties.class)
public class AssistantGenerationEventStreamConfiguration {

    @Bean
    ThreadPoolTaskScheduler assistantEventStreamScheduler(
            AssistantGenerationEventStreamProperties properties
    ) {
        properties.validate();
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getSchedulerThreads());
        scheduler.setThreadNamePrefix("assistant-event-stream-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}

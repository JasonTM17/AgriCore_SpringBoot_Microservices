package com.agricore.assistant.infrastructure.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantRetentionProperties.class)
public class AssistantPersistenceConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock assistantClock() {
        return Clock.systemUTC();
    }
}

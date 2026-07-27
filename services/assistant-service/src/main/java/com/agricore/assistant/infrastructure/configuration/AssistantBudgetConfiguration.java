package com.agricore.assistant.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AssistantBudgetProperties.class)
public class AssistantBudgetConfiguration {
}

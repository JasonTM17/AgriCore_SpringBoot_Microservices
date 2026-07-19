package com.agricore.assistant.infrastructure.configuration;

import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.infrastructure.provider.UnavailableChatProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantProviderProperties.class)
public class ChatProviderConfiguration {

    @Bean
    @ConditionalOnMissingBean(ChatProvider.class)
    ChatProvider unavailableChatProvider(AssistantProviderProperties properties) {
        return new UnavailableChatProvider(properties.getType());
    }
}

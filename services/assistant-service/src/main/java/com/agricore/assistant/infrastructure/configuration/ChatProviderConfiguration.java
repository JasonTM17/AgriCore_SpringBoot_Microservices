package com.agricore.assistant.infrastructure.configuration;

import com.agricore.assistant.application.port.ChatProvider;
import com.agricore.assistant.infrastructure.provider.SpringAiChatProviderFactory;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantProviderProperties.class)
public class ChatProviderConfiguration {

    @Bean
    SpringAiChatProviderFactory springAiChatProviderFactory(
            ObjectProvider<ObservationRegistry> observationRegistry
    ) {
        return new SpringAiChatProviderFactory(
                observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP)
        );
    }

    @Bean
    @ConditionalOnMissingBean(ChatProvider.class)
    ChatProvider chatProvider(
            AssistantProviderProperties properties,
            SpringAiChatProviderFactory factory
    ) {
        return factory.create(properties);
    }
}

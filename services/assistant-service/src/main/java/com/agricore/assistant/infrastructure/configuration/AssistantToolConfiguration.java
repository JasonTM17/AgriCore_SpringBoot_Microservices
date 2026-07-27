package com.agricore.assistant.infrastructure.configuration;

import com.agricore.assistant.infrastructure.tool.farm.FarmReadToolClient;
import com.agricore.security.AgricoreSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AssistantToolProperties.class, AgricoreSecurityProperties.class})
public class AssistantToolConfiguration {

    @Bean
    @ConditionalOnMissingBean(FarmReadToolClient.class)
    FarmReadToolClient farmReadToolClient(
            AssistantToolProperties properties,
            AgricoreSecurityProperties securityProperties,
            ObjectMapper objectMapper
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.validatedConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.validatedReadTimeout());
        return new FarmReadToolClient(
                RestClient.builder().requestFactory(requestFactory),
                objectMapper,
                properties,
                securityProperties.isDevMode()
        );
    }
}

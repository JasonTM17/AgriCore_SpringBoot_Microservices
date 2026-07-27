package com.agricore.work.infrastructure.client;

import com.agricore.security.AgricoreSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InventoryStockClientProperties.class)
public class InventoryStockClientConfiguration {

    @Bean
    InventoryStockClient inventoryStockClient(
            InventoryStockClientProperties properties,
            AgricoreSecurityProperties securityProperties,
            ObjectMapper objectMapper
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.validatedConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.validatedReadTimeout());
        return new DefaultInventoryStockClient(
                RestClient.builder().requestFactory(requestFactory),
                properties,
                securityProperties.isDevMode(),
                objectMapper
        );
    }
}

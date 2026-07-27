package com.agricore.harvest.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WarehouseAccessProperties.class)
class WarehouseAccessConfiguration {

    @Bean
    WarehouseAccessClient warehouseAccessClient(
            WarehouseAccessProperties properties,
            ObjectMapper objectMapper
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.validatedConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.validatedReadTimeout());
        return new DefaultWarehouseAccessClient(
                RestClient.builder().requestFactory(requestFactory),
                properties,
                objectMapper
        );
    }
}

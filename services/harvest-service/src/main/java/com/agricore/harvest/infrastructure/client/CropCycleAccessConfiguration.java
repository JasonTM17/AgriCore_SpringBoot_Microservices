package com.agricore.harvest.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CropCycleAccessProperties.class)
class CropCycleAccessConfiguration {

    @Bean
    CropCycleAccessClient cropCycleAccessClient(
            CropCycleAccessProperties properties,
            ObjectMapper objectMapper
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.validatedConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.validatedReadTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.validatedBaseUri().toString())
                .requestFactory(requestFactory)
                .build();
        return new DefaultCropCycleAccessClient(
                restClient,
                objectMapper,
                properties.validatedMaxResponseBytes()
        );
    }
}

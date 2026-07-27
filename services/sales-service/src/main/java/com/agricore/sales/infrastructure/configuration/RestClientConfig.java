package com.agricore.sales.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    RestClientCustomizer inventoryClientTimeouts(
            @Value("${agricore.inventory.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${agricore.inventory.read-timeout:PT5S}") Duration readTimeout
    ) {
        validateTimeout("connect-timeout", connectTimeout);
        validateTimeout("read-timeout", readTimeout);

        return builder -> {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(readTimeout);
            builder.requestFactory(requestFactory);
        };
    }

    private static void validateTimeout(String property, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "agricore.inventory." + property + " must be between PT0S and PT30S"
            );
        }
    }
}

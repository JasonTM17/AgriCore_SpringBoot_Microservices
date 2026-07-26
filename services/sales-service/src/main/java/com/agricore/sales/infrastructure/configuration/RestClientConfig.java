package com.agricore.sales.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Bounds every outbound call the order saga makes.
 *
 * <p>Without timeouts an inventory instance that accepts the connection and then stalls holds the
 * calling request thread indefinitely. That is worse here than a plain slow dependency: the saga
 * blocks between reserving and confirming, so the reservation stays open for as long as the socket
 * does, and enough concurrent orders exhaust the servlet pool.
 *
 * <p>Registered as a customizer rather than by declaring a {@code RestClient.Builder} bean.
 * Declaring the builder shadows Boot's auto-configured one and silently drops the customizers it
 * carries; a customizer composes with them instead.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClientCustomizer inventoryClientTimeouts(
            @Value("${agricore.inventory.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${agricore.inventory.read-timeout-ms:5000}") long readTimeoutMs
    ) {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
            factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
            builder.requestFactory(factory);
        };
    }
}

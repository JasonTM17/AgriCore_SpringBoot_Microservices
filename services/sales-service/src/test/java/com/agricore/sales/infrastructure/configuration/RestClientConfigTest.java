package com.agricore.sales.infrastructure.configuration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientConfigTest {

    private final RestClientConfig config = new RestClientConfig();

    @Test
    void rejectsUnboundedOrInvalidTimeouts() {
        assertThatThrownBy(() -> config.restClientBuilder(Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connect-timeout");
        assertThatThrownBy(() -> config.restClientBuilder(Duration.ofSeconds(1), Duration.ofSeconds(31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read-timeout");
    }

    @Test
    void abortsAnInventoryResponseThatExceedsTheReadTimeout() throws IOException {
        var executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            var client = config.restClientBuilder(
                            Duration.ofMillis(200),
                            Duration.ofMillis(100)
                    )
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .build();

            assertThatThrownBy(() -> client.get().uri("/").retrieve().toBodilessEntity())
                    .isInstanceOf(ResourceAccessException.class);
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}

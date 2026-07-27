package com.agricore.harvest.infrastructure.client;

import com.agricore.harvest.domain.exception.HarvestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DefaultWarehouseAccessClientTest {

    private static final String BASE_URL = "http://localhost:8086";
    private static final String INTERNAL_TOKEN =
            "test-inventory-harvest-service-token-012345678901234567890123";

    private final List<MockRestServiceServer> servers = new ArrayList<>();
    private MockRestServiceServer server;

    @AfterEach
    void verifyServer() {
        servers.forEach(MockRestServiceServer::verify);
    }

    @Test
    void requireWarehouse_acceptsAuthoritativeSameFarmScope() {
        UUID warehouseId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        DefaultWarehouseAccessClient client = client();
        server.expect(once(), requestTo(scopeUrl(warehouseId)))
                .andExpect(header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andRespond(withSuccess(response(warehouseId, farmId), MediaType.APPLICATION_JSON));

        client.requireWarehouse(warehouseId, farmId);
    }

    @Test
    void requireWarehouse_mapsMissingWarehouseToNotFound() {
        UUID warehouseId = UUID.randomUUID();
        DefaultWarehouseAccessClient client = client();
        server.expect(once(), requestTo(scopeUrl(warehouseId)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFailure(
                () -> client.requireWarehouse(warehouseId, UUID.randomUUID()),
                HttpStatus.NOT_FOUND,
                "WAREHOUSE_NOT_FOUND"
        );
    }

    @Test
    void requireWarehouse_masksCrossFarmWarehouseAsNotFound() {
        UUID warehouseId = UUID.randomUUID();
        DefaultWarehouseAccessClient client = client();
        server.expect(once(), requestTo(scopeUrl(warehouseId)))
                .andRespond(withSuccess(
                        response(warehouseId, UUID.randomUUID()),
                        MediaType.APPLICATION_JSON
                ));

        assertFailure(
                () -> client.requireWarehouse(warehouseId, UUID.randomUUID()),
                HttpStatus.NOT_FOUND,
                "WAREHOUSE_NOT_FOUND"
        );
    }

    @Test
    void requireWarehouse_failsClosedForDownstreamAndInvalidResponses() {
        UUID unavailableWarehouseId = UUID.randomUUID();
        DefaultWarehouseAccessClient unavailableClient = client();
        server.expect(once(), requestTo(scopeUrl(unavailableWarehouseId)))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertUnavailable(() -> unavailableClient.requireWarehouse(
                unavailableWarehouseId,
                UUID.randomUUID()
        ));

        UUID mismatchedWarehouseId = UUID.randomUUID();
        DefaultWarehouseAccessClient mismatchedClient = client();
        server.expect(once(), requestTo(scopeUrl(mismatchedWarehouseId)))
                .andRespond(withSuccess(
                        response(UUID.randomUUID(), UUID.randomUUID()),
                        MediaType.APPLICATION_JSON
                ));
        assertUnavailable(() -> mismatchedClient.requireWarehouse(
                mismatchedWarehouseId,
                UUID.randomUUID()
        ));

        UUID malformedWarehouseId = UUID.randomUUID();
        DefaultWarehouseAccessClient malformedClient = client();
        server.expect(once(), requestTo(scopeUrl(malformedWarehouseId)))
                .andRespond(withSuccess(
                        "{\"warehouseId\":\"not-a-uuid\",\"farmId\":\"not-a-uuid\"}",
                        MediaType.APPLICATION_JSON
                ));
        assertUnavailable(() -> malformedClient.requireWarehouse(
                malformedWarehouseId,
                UUID.randomUUID()
        ));
    }

    @Test
    void requireWarehouse_failsClosedWithoutConfiguredServiceToken() {
        WarehouseAccessProperties properties = properties();
        properties.setInternalServiceToken("");
        DefaultWarehouseAccessClient client = client(properties);

        assertUnavailable(() -> client.requireWarehouse(
                UUID.randomUUID(),
                UUID.randomUUID()
        ));
    }

    @Test
    void requireWarehouse_rejectsOversizedResponse() {
        UUID warehouseId = UUID.randomUUID();
        WarehouseAccessProperties properties = properties();
        properties.setMaxResponseBytes(256);
        DefaultWarehouseAccessClient client = client(properties);
        server.expect(once(), requestTo(scopeUrl(warehouseId)))
                .andRespond(withSuccess("x".repeat(257), MediaType.APPLICATION_JSON));

        assertUnavailable(() -> client.requireWarehouse(warehouseId, UUID.randomUUID()));
    }

    @Test
    void properties_rejectUntrustedHostsAndUnboundedTimeouts() {
        WarehouseAccessProperties untrustedHost = properties();
        untrustedHost.setBaseUrl("https://inventory.example.invalid");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(untrustedHost::validatedBaseUri)
                .withMessageContaining("host is not in allowed-hosts");

        WarehouseAccessProperties zeroTimeout = properties();
        zeroTimeout.setConnectTimeout(Duration.ZERO);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(zeroTimeout::validatedConnectTimeout)
                .withMessageContaining("connect-timeout");

        WarehouseAccessProperties excessiveTimeout = properties();
        excessiveTimeout.setReadTimeout(Duration.ofSeconds(31));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(excessiveTimeout::validatedReadTimeout)
                .withMessageContaining("read-timeout");
    }

    private DefaultWarehouseAccessClient client() {
        return client(properties());
    }

    private DefaultWarehouseAccessClient client(WarehouseAccessProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        servers.add(server);
        return new DefaultWarehouseAccessClient(builder, properties, new ObjectMapper());
    }

    private static WarehouseAccessProperties properties() {
        WarehouseAccessProperties properties = new WarehouseAccessProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setInternalServiceToken(INTERNAL_TOKEN);
        return properties;
    }

    private static void assertUnavailable(ThrowingCall call) {
        assertFailure(
                call,
                HttpStatus.SERVICE_UNAVAILABLE,
                "WAREHOUSE_ACCESS_UNAVAILABLE"
        );
    }

    private static void assertFailure(
            ThrowingCall call,
            HttpStatus status,
            String code
    ) {
        assertThatExceptionOfType(HarvestException.class)
                .isThrownBy(call::execute)
                .satisfies(exception -> {
                    assertThat(exception.getHttpStatus()).isEqualTo(status.value());
                    assertThat(exception.getCode()).isEqualTo(code);
                });
    }

    private static String response(UUID warehouseId, UUID farmId) {
        return """
                {"warehouseId":"%s","farmId":"%s"}
                """.formatted(warehouseId, farmId);
    }

    private static String scopeUrl(UUID warehouseId) {
        return BASE_URL + "/internal/api/v1/inventory/warehouses/"
                + warehouseId
                + "/scope";
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void execute();
    }
}

package com.agricore.work.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class DefaultInventoryStockClientTest {

    private static final String STOCK_OUT_PATH = "/internal/api/v1/inventory/stock-out";
    private static final String INTERNAL_TOKEN =
            "test-inventory-work-service-token-012345678901234567890123";
    private static final UUID FARM_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private final List<WireMockServer> servers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        servers.forEach(WireMockServer::stop);
    }

    @Test
    void stockOutForwardsCallerJwtAndValidatesAuthoritativeItem() {
        UUID itemId = UUID.randomUUID();
        setJwtAuthentication("signed-access-token");
        TestClient fixture = client(false);
        fixture.server().stubFor(post(urlEqualTo(STOCK_OUT_PATH))
                .willReturn(okJson(response(itemId))));

        InventoryStockClient.StockOutResult result = fixture.client().stockOut(
                FARM_ID, itemId, new BigDecimal("2.500"), "material-ref"
        );

        assertThat(result.inventoryItemId()).isEqualTo(itemId);
        assertThat(result.unit()).isEqualTo("KG");
        fixture.server().verify(postRequestedFor(urlEqualTo(STOCK_OUT_PATH))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer signed-access-token"))
                .withHeader("X-Internal-Service-Token", equalTo(INTERNAL_TOKEN))
                .withRequestBody(matchingJsonPath("$.farmId", equalTo(FARM_ID.toString())))
                .withRequestBody(matchingJsonPath("$.inventoryItemId", equalTo(itemId.toString())))
                .withRequestBody(matchingJsonPath("$.referenceType", equalTo("WorkTask"))));
    }

    @Test
    void stockOutForwardsAuthenticatedDevCallerRoles() {
        UUID itemId = UUID.randomUUID();
        setDevAuthentication("worker-a", "ROLE_FIELD_WORKER", "ROLE_AGRONOMIST");
        TestClient fixture = client(true);
        fixture.server().stubFor(post(urlEqualTo(STOCK_OUT_PATH))
                .willReturn(okJson(response(itemId))));

        fixture.client().stockOut(FARM_ID, itemId, BigDecimal.ONE, "material-ref");

        fixture.server().verify(postRequestedFor(urlEqualTo(STOCK_OUT_PATH))
                .withHeader("X-Dev-User", equalTo("worker-a"))
                .withHeader("X-Dev-Roles", equalTo("AGRONOMIST,FIELD_WORKER"))
                .withHeader("X-Internal-Service-Token", equalTo(INTERNAL_TOKEN)));
    }

    @Test
    void stockOutPreservesConflictStatusForPendingWorkflow() {
        UUID itemId = UUID.randomUUID();
        setDevAuthentication("worker-b", "ROLE_FIELD_WORKER");
        TestClient fixture = client(true);
        fixture.server().stubFor(post(urlEqualTo(STOCK_OUT_PATH))
                .willReturn(aResponse().withStatus(HttpStatus.CONFLICT.value())));

        assertThatExceptionOfType(InventoryStockClientException.class)
                .isThrownBy(() -> fixture.client().stockOut(
                        FARM_ID, itemId, BigDecimal.TEN, "material-ref"
                ))
                .satisfies(exception -> assertThat(exception.getDownstreamStatus()).isEqualTo(409));
    }

    @Test
    void stockOutRejectsUnknownFieldsAndMismatchedItemIdentity() {
        UUID itemId = UUID.randomUUID();
        setDevAuthentication("worker-c", "ROLE_FIELD_WORKER");
        TestClient unknownField = client(true);
        unknownField.server().stubFor(post(urlEqualTo(STOCK_OUT_PATH))
                .willReturn(okJson(response(itemId).replace("}", ",\"unknown\":true}"))));
        assertUnavailable(() -> unknownField.client().stockOut(
                FARM_ID, itemId, BigDecimal.ONE, "material-ref"
        ));

        TestClient mismatch = client(true);
        mismatch.server().stubFor(post(urlEqualTo(STOCK_OUT_PATH))
                .willReturn(okJson(response(UUID.randomUUID()))));
        assertUnavailable(() -> mismatch.client().stockOut(
                FARM_ID, itemId, BigDecimal.ONE, "material-ref"
        ));
    }

    @Test
    void stockOutFailsClosedWithoutForwardableProductionAuthentication() {
        TestClient fixture = client(false);
        assertUnavailable(() -> fixture.client().stockOut(
                FARM_ID, UUID.randomUUID(), BigDecimal.ONE, "material-ref"
        ));
        fixture.server().verify(0, postRequestedFor(urlEqualTo(STOCK_OUT_PATH)));
    }

    private TestClient client(boolean devMode) {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        servers.add(server);
        InventoryStockClientProperties properties = new InventoryStockClientProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.port());
        properties.setInternalServiceToken(INTERNAL_TOKEN);
        return new TestClient(
                new DefaultInventoryStockClient(
                        RestClient.builder(), properties, devMode, new ObjectMapper()
                ),
                server
        );
    }

    private static void assertUnavailable(ThrowingCall call) {
        assertThatExceptionOfType(InventoryStockClientException.class)
                .isThrownBy(call::execute)
                .satisfies(exception -> assertThat(exception.getDownstreamStatus()).isEqualTo(503));
    }

    private static String response(UUID itemId) {
        return """
                {
                  "id":"%s",
                  "warehouseId":"%s",
                  "sku":"FERT-NPK",
                  "name":"NPK Fertilizer",
                  "itemType":"MATERIAL",
                  "unit":"KG",
                  "onHandQuantity":7.500,
                  "reservedQuantity":1.000,
                  "availableQuantity":6.500,
                  "version":2
                }
                """.formatted(itemId, UUID.randomUUID());
    }

    private static void setJwtAuthentication(String tokenValue) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject("worker-a")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_FIELD_WORKER")),
                "worker-a"
        ));
    }

    private static void setDevAuthentication(String subject, String... roles) {
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(subject, null, authorities)
        );
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void execute();
    }

    private record TestClient(DefaultInventoryStockClient client, WireMockServer server) {
    }
}

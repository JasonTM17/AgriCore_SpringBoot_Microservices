package com.agricore.work.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DefaultInventoryStockClientTest {

    private static final String BASE_URL = "https://inventory-service";
    private static final UUID FARM_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stockOutForwardsCallerJwtAndValidatesAuthoritativeItem() {
        UUID itemId = UUID.randomUUID();
        setJwtAuthentication("signed-access-token");
        TestClient fixture = client(false);
        fixture.server().expect(once(), requestTo(BASE_URL + "/internal/api/v1/inventory/stock-out"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer signed-access-token"))
                .andExpect(header("X-Internal-Service-Token", "test-inventory-work-service-token-012345678901234567890123"))
                .andExpect(jsonPath("$.farmId").value(FARM_ID.toString()))
                .andExpect(jsonPath("$.inventoryItemId").value(itemId.toString()))
                .andExpect(jsonPath("$.referenceType").value("WorkTask"))
                .andRespond(withSuccess(response(itemId), MediaType.APPLICATION_JSON));

        InventoryStockClient.StockOutResult result = fixture.client().stockOut(
                FARM_ID, itemId, new BigDecimal("2.500"), "material-ref"
        );

        assertThat(result.inventoryItemId()).isEqualTo(itemId);
        assertThat(result.unit()).isEqualTo("KG");
        fixture.server().verify();
    }

    @Test
    void stockOutForwardsAuthenticatedDevCallerRoles() {
        UUID itemId = UUID.randomUUID();
        setDevAuthentication("worker-a", "ROLE_FIELD_WORKER", "ROLE_AGRONOMIST");
        TestClient fixture = client(true);
        fixture.server().expect(once(), requestTo(BASE_URL + "/internal/api/v1/inventory/stock-out"))
                .andExpect(header("X-Dev-User", "worker-a"))
                .andExpect(header("X-Dev-Roles", "AGRONOMIST,FIELD_WORKER"))
                .andExpect(header("X-Internal-Service-Token", "test-inventory-work-service-token-012345678901234567890123"))
                .andRespond(withSuccess(response(itemId), MediaType.APPLICATION_JSON));

        fixture.client().stockOut(FARM_ID, itemId, BigDecimal.ONE, "material-ref");

        fixture.server().verify();
    }

    @Test
    void stockOutPreservesConflictStatusForPendingWorkflow() {
        UUID itemId = UUID.randomUUID();
        setDevAuthentication("worker-b", "ROLE_FIELD_WORKER");
        TestClient fixture = client(true);
        fixture.server().expect(once(), requestTo(BASE_URL + "/internal/api/v1/inventory/stock-out"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT));

        assertThatExceptionOfType(InventoryStockClientException.class)
                .isThrownBy(() -> fixture.client().stockOut(FARM_ID, itemId, BigDecimal.TEN, "material-ref"))
                .satisfies(exception -> assertThat(exception.getDownstreamStatus()).isEqualTo(409));
    }

    @Test
    void stockOutRejectsUnknownFieldsAndMismatchedItemIdentity() {
        UUID itemId = UUID.randomUUID();
        setDevAuthentication("worker-c", "ROLE_FIELD_WORKER");
        TestClient unknownField = client(true);
        unknownField.server().expect(once(), requestTo(BASE_URL + "/internal/api/v1/inventory/stock-out"))
                .andRespond(withSuccess(response(itemId).replace("}", ",\"unknown\":true}"), MediaType.APPLICATION_JSON));
        assertThatExceptionOfType(InventoryStockClientException.class)
                .isThrownBy(() -> unknownField.client().stockOut(FARM_ID, itemId, BigDecimal.ONE, "material-ref"))
                .satisfies(exception -> assertThat(exception.getDownstreamStatus()).isEqualTo(503));

        TestClient mismatch = client(true);
        mismatch.server().expect(once(), requestTo(BASE_URL + "/internal/api/v1/inventory/stock-out"))
                .andRespond(withSuccess(response(UUID.randomUUID()), MediaType.APPLICATION_JSON));
        assertThatExceptionOfType(InventoryStockClientException.class)
                .isThrownBy(() -> mismatch.client().stockOut(FARM_ID, itemId, BigDecimal.ONE, "material-ref"))
                .satisfies(exception -> assertThat(exception.getDownstreamStatus()).isEqualTo(503));
    }

    @Test
    void stockOutFailsClosedWithoutForwardableProductionAuthentication() {
        TestClient fixture = client(false);
        assertThatExceptionOfType(InventoryStockClientException.class)
                .isThrownBy(() -> fixture.client().stockOut(
                        FARM_ID, UUID.randomUUID(), BigDecimal.ONE, "material-ref"
                ))
                .satisfies(exception -> assertThat(exception.getDownstreamStatus()).isEqualTo(503));
    }

    private static TestClient client(boolean devMode) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InventoryStockClientProperties properties = new InventoryStockClientProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setInternalServiceToken("test-inventory-work-service-token-012345678901234567890123");
        return new TestClient(
                new DefaultInventoryStockClient(builder, properties, devMode, new ObjectMapper()),
                server
        );
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

    private record TestClient(DefaultInventoryStockClient client, MockRestServiceServer server) {
    }
}

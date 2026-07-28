package com.agricore.sales;

import com.agricore.sales.infrastructure.client.InventoryClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

import static com.agricore.sales.infrastructure.client.InventoryClient.ReleaseOutcome.FULFILLED;
import static com.agricore.sales.infrastructure.client.InventoryClient.ReleaseOutcome.RELEASED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class InventoryClientTest {

    private static final String BASE_URL = "https://inventory-service";
    private static final String INTERNAL_BASE_PATH = "/internal/api/v1/inventory";
    private static final String INTERNAL_TOKEN =
            "test-inventory-sales-service-token-012345678901234567890123";
    private static final String SAFE_DOWNSTREAM_FAILURE =
            "Inventory request failed (status=503, code=INVENTORY_DOWNSTREAM_ERROR)";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reserve_postsAuthoritativeContractAndForwardsCallerBearerToken() {
        UUID farmId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String referenceId = UUID.randomUUID().toString();
        setBearerToken("sales-caller-token");
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(BASE_URL + INTERNAL_BASE_PATH + "/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andExpect(header("Authorization", "Bearer sales-caller-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "farmId":"%s",
                          "inventoryItemId":"%s",
                          "quantity":12.500,
                          "referenceType":"SalesOrder",
                          "referenceId":"%s"
                        }
                        """.formatted(farmId, itemId, referenceId)))
                .andRespond(withSuccess("""
                        {"id":"%s"}
                        """.formatted(reservationId), MediaType.APPLICATION_JSON));

        assertThat(fixture.client().reserve(farmId, itemId, new BigDecimal("12.500"), referenceId))
                .isEqualTo(reservationId);
        fixture.server().verify();
    }

    @Test
    void confirm_postsAuthoritativeContractAndForwardsCallerBearerToken() {
        UUID farmId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        setBearerToken("sales-caller-token");
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        BASE_URL + INTERNAL_BASE_PATH + "/reservations/" + reservationId
                                + "/confirm?farmId=" + farmId))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andExpect(header("Authorization", "Bearer sales-caller-token"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        fixture.client().confirm(farmId, reservationId);

        fixture.server().verify();
    }

    @Test
    void reserveClassifiesOnlyExplicitInsufficientStockCode() {
        UUID farmId = UUID.randomUUID();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(BASE_URL + INTERNAL_BASE_PATH + "/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"INSUFFICIENT_STOCK","message":"Not enough available stock"}
                                """));

        assertThatExceptionOfType(InventoryClient.InventoryReservationException.class)
                .isThrownBy(() -> fixture.client().reserve(
                        farmId,
                        UUID.randomUUID(),
                        new BigDecimal("5.000"),
                        UUID.randomUUID().toString()
                ))
                .satisfies(exception -> {
                    assertThat(exception.isInsufficientStock()).isTrue();
                    assertThat(exception.getMessage())
                            .isEqualTo("Inventory request failed (status=409, code=INSUFFICIENT_STOCK)")
                            .doesNotContain("Not enough available stock");
                });
        fixture.server().verify();
    }

    @Test
    void reserveReferenceConflictIsNotMisclassifiedAsInsufficientStock() {
        UUID farmId = UUID.randomUUID();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(BASE_URL + INTERNAL_BASE_PATH + "/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"RESERVATION_REFERENCE_CONFLICT","message":"Reference conflict"}
                                """));

        assertThatExceptionOfType(InventoryClient.InventoryReservationException.class)
                .isThrownBy(() -> fixture.client().reserve(
                        farmId,
                        UUID.randomUUID(),
                        new BigDecimal("5.000"),
                        UUID.randomUUID().toString()
                ))
                .satisfies(exception -> assertThat(exception.isInsufficientStock()).isFalse());
        fixture.server().verify();
    }

    @Test
    void reserveRedactsLargeJsonErrorBody() {
        String sensitiveValue = "inventory-db-password=super-secret";
        String responseBody = """
                {"code":"UNTRUSTED_ERROR","message":"%s","details":"%s"}
                """.formatted(sensitiveValue, "x".repeat(8_192));
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(BASE_URL + INTERNAL_BASE_PATH + "/reservations"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));

        assertThatExceptionOfType(InventoryClient.InventoryReservationException.class)
                .isThrownBy(() -> fixture.client().reserve(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("5.000"),
                        UUID.randomUUID().toString()
                ))
                .satisfies(exception -> {
                    assertThat(exception.getMessage())
                            .isEqualTo(SAFE_DOWNSTREAM_FAILURE)
                            .doesNotContain(sensitiveValue, responseBody)
                            .hasSizeLessThanOrEqualTo(160);
                    assertThat(exception.getStatus()).isEqualTo(503);
                });
        fixture.server().verify();
    }

    @Test
    void confirmRedactsLargeHtmlErrorBody() {
        UUID farmId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String sensitiveValue = "session-token=top-secret";
        String responseBody = "<html><body>" + sensitiveValue + "x".repeat(8_192) + "</body></html>";
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        BASE_URL + INTERNAL_BASE_PATH + "/reservations/" + reservationId
                                + "/confirm?farmId=" + farmId))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.TEXT_HTML)
                        .body(responseBody));

        assertThatExceptionOfType(InventoryClient.InventoryReservationException.class)
                .isThrownBy(() -> fixture.client().confirm(farmId, reservationId))
                .satisfies(exception -> {
                    assertThat(exception.getMessage())
                            .isEqualTo(SAFE_DOWNSTREAM_FAILURE)
                            .doesNotContain(sensitiveValue, responseBody)
                            .hasSizeLessThanOrEqualTo(160);
                    assertThat(exception.getStatus()).isEqualTo(503);
                });
        fixture.server().verify();
    }

    @Test
    void findByReference_readsAuthoritativeReservationState() {
        UUID reservationId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        URI.create(BASE_URL + INTERNAL_BASE_PATH + "/reservations/by-reference"
                                + "?farmId=" + farmId
                                + "&referenceType=SalesOrder&referenceId=" + orderId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id":"%s",
                          "inventoryItemId":"%s",
                          "quantity":12.500,
                          "status":"ACTIVE",
                          "referenceType":"SalesOrder",
                          "referenceId":"%s"
                        }
                        """.formatted(reservationId, itemId, orderId), MediaType.APPLICATION_JSON));

        assertThat(fixture.client().findByReference(farmId, "SalesOrder", orderId))
                .hasValueSatisfying(state -> {
                    assertThat(state.id()).isEqualTo(reservationId);
                    assertThat(state.inventoryItemId()).isEqualTo(itemId);
                    assertThat(state.quantity()).isEqualByComparingTo("12.500");
                    assertThat(state.status()).isEqualTo("ACTIVE");
                });
        fixture.server().verify();
    }

    @Test
    void findByReference_treatsNotFoundAsNoReservation() {
        UUID farmId = UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        URI.create(BASE_URL + INTERNAL_BASE_PATH + "/reservations/by-reference"
                                + "?farmId=" + farmId
                                + "&referenceType=SalesOrder&referenceId=" + orderId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"RESERVATION_NOT_FOUND\"}"));

        assertThat(fixture.client().findByReference(farmId, "SalesOrder", orderId)).isEmpty();
        fixture.server().verify();
    }

    @Test
    void findByReference_rejectsMalformedReservationPayloadWithoutExposingIt() {
        UUID farmId = UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();
        String sensitiveValue = "inventory-lookup-token=super-secret";
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        URI.create(BASE_URL + INTERNAL_BASE_PATH + "/reservations/by-reference"
                                + "?farmId=" + farmId
                                + "&referenceType=SalesOrder&referenceId=" + orderId)))
                .andRespond(withSuccess("""
                        {
                          "id":"not-a-uuid",
                          "inventoryItemId":"%s",
                          "quantity":2.500,
                          "status":"ACTIVE",
                          "diagnostic":"%s"
                        }
                        """.formatted(UUID.randomUUID(), sensitiveValue), MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(InventoryClient.InventoryReservationException.class)
                .isThrownBy(() -> fixture.client().findByReference(farmId, "SalesOrder", orderId))
                .satisfies(exception -> {
                    assertThat(exception.getStatus()).isEqualTo(502);
                    assertThat(exception.getMessage())
                            .isEqualTo("Inventory request failed (status=502, code=INVALID_INVENTORY_RESPONSE)")
                            .doesNotContain(sensitiveValue);
                });
        fixture.server().verify();
    }

    @Test
    void findByReference_discardsMalformedDownstreamErrorPayload() {
        UUID farmId = UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();
        String sensitiveValue = "inventory-lookup-token=super-secret";
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        URI.create(BASE_URL + INTERNAL_BASE_PATH + "/reservations/by-reference"
                                + "?farmId=" + farmId
                                + "&referenceType=SalesOrder&referenceId=" + orderId)))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"INSUFFICIENT_STOCK\",\"message\":\"" + sensitiveValue));

        assertThatExceptionOfType(InventoryClient.InventoryReservationException.class)
                .isThrownBy(() -> fixture.client().findByReference(farmId, "SalesOrder", orderId))
                .satisfies(exception -> {
                    assertThat(exception.getStatus()).isEqualTo(503);
                    assertThat(exception.getMessage())
                            .isEqualTo(SAFE_DOWNSTREAM_FAILURE)
                            .doesNotContain(sensitiveValue);
                });
        fixture.server().verify();
    }

    @Test
    void release_returnsAuthoritativeFulfilledOutcome() {
        UUID farmId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        BASE_URL + INTERNAL_BASE_PATH + "/reservations/" + reservationId
                                + "/release?farmId=" + farmId))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"id":"%s","status":"FULFILLED"}
                        """.formatted(reservationId), MediaType.APPLICATION_JSON));

        assertThat(fixture.client().release(farmId, reservationId)).isEqualTo(FULFILLED);
        fixture.server().verify();
    }

    @Test
    void release_returnsAuthoritativeReleasedOutcome() {
        UUID farmId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        BASE_URL + INTERNAL_BASE_PATH + "/reservations/" + reservationId
                                + "/release?farmId=" + farmId))
                .andRespond(withSuccess("""
                        {"id":"%s","status":"RELEASED"}
                        """.formatted(reservationId), MediaType.APPLICATION_JSON));

        assertThat(fixture.client().release(farmId, reservationId)).isEqualTo(RELEASED);
        fixture.server().verify();
    }

    @Test
    void release_rejectsUnexpectedInventoryStatus() {
        UUID farmId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        BASE_URL + INTERNAL_BASE_PATH + "/reservations/" + reservationId
                                + "/release?farmId=" + farmId))
                .andRespond(withSuccess("""
                        {"id":"%s","status":"ACTIVE"}
                        """.formatted(reservationId), MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(InventoryClient.InventoryReservationException.class)
                .isThrownBy(() -> fixture.client().release(farmId, reservationId))
                .satisfies(exception -> assertThat(exception.getStatus()).isEqualTo(502));
        fixture.server().verify();
    }

    @Test
    void release_rejectsEmptyInventoryResponse() {
        UUID farmId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        BASE_URL + INTERNAL_BASE_PATH + "/reservations/" + reservationId
                                + "/release?farmId=" + farmId))
                .andRespond(withSuccess());

        assertThatExceptionOfType(InventoryClient.InventoryReservationException.class)
                .isThrownBy(() -> fixture.client().release(farmId, reservationId))
                .withMessage("Inventory request failed (status=502, code=INVALID_INVENTORY_RESPONSE)")
                .satisfies(exception -> assertThat(exception.getStatus()).isEqualTo(502));
        fixture.server().verify();
    }

    private static TestClient client() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InventoryClient client = new InventoryClient(builder, new ObjectMapper(), BASE_URL, INTERNAL_TOKEN);
        return new TestClient(client, server);
    }

    private static void setBearerToken(String tokenValue) {
        Jwt token = Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .claim("sub", "sales-caller")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
    }

    private record TestClient(InventoryClient client, MockRestServiceServer server) {
    }
}

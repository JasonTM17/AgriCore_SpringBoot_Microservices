package com.agricore.sales;

import com.agricore.sales.infrastructure.client.InventoryClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class InventoryClientTest {

    private static final String BASE_URL = "https://inventory-service";
    private static final String INTERNAL_BASE_PATH = "/internal/api/v1/inventory";
    private static final String INTERNAL_TOKEN =
            "test-inventory-sales-service-token-012345678901234567890123";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reserveClassifiesOnlyExplicitInsufficientStockCode() {
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
                        UUID.randomUUID(),
                        new BigDecimal("5.000"),
                        UUID.randomUUID().toString()
                ))
                .satisfies(exception -> assertThat(exception.isInsufficientStock()).isTrue());
        fixture.server().verify();
    }

    @Test
    void reserveReferenceConflictIsNotMisclassifiedAsInsufficientStock() {
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
                        UUID.randomUUID(),
                        new BigDecimal("5.000"),
                        UUID.randomUUID().toString()
                ))
                .satisfies(exception -> assertThat(exception.isInsufficientStock()).isFalse());
        fixture.server().verify();
    }

    @Test
    void findByReference_readsAuthoritativeReservationState() {
        UUID reservationId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        URI.create(BASE_URL + INTERNAL_BASE_PATH + "/reservations/by-reference"
                                + "?referenceType=SalesOrder&referenceId=" + orderId)))
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

        assertThat(fixture.client().findByReference("SalesOrder", orderId))
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
        String orderId = UUID.randomUUID().toString();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        URI.create(BASE_URL + INTERNAL_BASE_PATH + "/reservations/by-reference"
                                + "?referenceType=SalesOrder&referenceId=" + orderId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"RESERVATION_NOT_FOUND\"}"));

        assertThat(fixture.client().findByReference("SalesOrder", orderId)).isEmpty();
        fixture.server().verify();
    }

    @Test
    void release_returnsAuthoritativeFulfilledOutcome() {
        UUID reservationId = UUID.randomUUID();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        BASE_URL + INTERNAL_BASE_PATH + "/reservations/" + reservationId + "/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"id":"%s","status":"FULFILLED"}
                        """.formatted(reservationId), MediaType.APPLICATION_JSON));

        assertThat(fixture.client().release(reservationId)).isEqualTo(FULFILLED);
        fixture.server().verify();
    }

    @Test
    void release_returnsAuthoritativeReleasedOutcome() {
        UUID reservationId = UUID.randomUUID();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        BASE_URL + INTERNAL_BASE_PATH + "/reservations/" + reservationId + "/release"))
                .andRespond(withSuccess("""
                        {"id":"%s","status":"RELEASED"}
                        """.formatted(reservationId), MediaType.APPLICATION_JSON));

        assertThat(fixture.client().release(reservationId)).isEqualTo(RELEASED);
        fixture.server().verify();
    }

    @Test
    void release_rejectsUnexpectedInventoryStatus() {
        UUID reservationId = UUID.randomUUID();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        BASE_URL + INTERNAL_BASE_PATH + "/reservations/" + reservationId + "/release"))
                .andRespond(withSuccess("""
                        {"id":"%s","status":"ACTIVE"}
                        """.formatted(reservationId), MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(InventoryClient.InventoryReservationException.class)
                .isThrownBy(() -> fixture.client().release(reservationId))
                .satisfies(exception -> assertThat(exception.getStatus()).isEqualTo(502));
        fixture.server().verify();
    }

    @Test
    void release_rejectsEmptyInventoryResponse() {
        UUID reservationId = UUID.randomUUID();
        TestClient fixture = client();
        fixture.server().expect(once(), requestTo(
                        BASE_URL + INTERNAL_BASE_PATH + "/reservations/" + reservationId + "/release"))
                .andRespond(withSuccess());

        assertThatExceptionOfType(InventoryClient.InventoryReservationException.class)
                .isThrownBy(() -> fixture.client().release(reservationId))
                .withMessage("Inventory release response was empty")
                .satisfies(exception -> assertThat(exception.getStatus()).isEqualTo(502));
        fixture.server().verify();
    }

    private static TestClient client() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InventoryClient client = new InventoryClient(builder, new ObjectMapper(), BASE_URL, INTERNAL_TOKEN);
        return new TestClient(client, server);
    }

    private record TestClient(InventoryClient client, MockRestServiceServer server) {
    }
}

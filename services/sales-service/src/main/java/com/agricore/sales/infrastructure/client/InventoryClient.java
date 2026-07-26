package com.agricore.sales.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Sync HTTP client to inventory-service for saga reserve/release steps.
 * Forwards the caller's Bearer JWT when present; falls back to X-Dev headers only in dev-mode.
 */
@Component
public class InventoryClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean securityDevMode;

    public InventoryClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${agricore.inventory.base-url}") String baseUrl,
            @Value("${agricore.security.dev-mode:false}") boolean securityDevMode
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.securityDevMode = securityDevMode;
    }

    public UUID reserve(UUID inventoryItemId, BigDecimal quantity, String referenceId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("inventoryItemId", inventoryItemId.toString());
            payload.put("quantity", quantity);
            payload.put("referenceType", "SalesOrder");
            payload.put("referenceId", referenceId);

            String body = restClient.post()
                    .uri("/api/v1/inventory/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(authHeaders())
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(body);
            return UUID.fromString(json.get("id").asText());
        } catch (RestClientResponseException ex) {
            throw new InventoryReservationException(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (InventoryReservationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InventoryReservationException(500, ex.getMessage() == null ? "inventory call failed" : ex.getMessage());
        }
    }

    public void release(UUID reservationId) {
        restClient.post()
                .uri("/api/v1/inventory/reservations/{id}/release", reservationId)
                .headers(authHeaders())
                .retrieve()
                .toBodilessEntity();
    }

    public void confirm(UUID reservationId) {
        try {
            restClient.post()
                    .uri("/api/v1/inventory/reservations/{id}/confirm", reservationId)
                    .headers(authHeaders())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new InventoryReservationException(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        }
    }

    private Consumer<HttpHeaders> authHeaders() {
        return headers -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                headers.setBearerAuth(jwtAuth.getToken().getTokenValue());
                return;
            }
            // Dev/test only — never used when AGRICORE_DEV_MODE=false in production compose
            if (securityDevMode) {
                headers.set("X-Dev-User", "sales-service");
                headers.set("X-Dev-Roles", "SALES_STAFF");
            }
        };
    }

    public static class InventoryReservationException extends RuntimeException {
        private final int status;

        public InventoryReservationException(int status, String body) {
            super(body);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }

        /**
         * True only for a genuine stock shortage, which is a terminal outcome for the order.
         *
         * <p>Deliberately does not treat every 409 as out of stock. Inventory also answers 409 for
         * {@code OPTIMISTIC_LOCK} (two orders touching one item at once) and
         * {@code RESERVATION_NOT_ACTIVE}. Reading the status alone marked the loser of a harmless
         * concurrent update as permanently OUT_OF_STOCK while the stock was in fact available.
         */
        public boolean isInsufficientStock() {
            return getMessage() != null && getMessage().contains("INSUFFICIENT_STOCK");
        }
    }
}

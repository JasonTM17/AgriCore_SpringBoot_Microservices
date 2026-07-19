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

    public ReleaseOutcome release(UUID reservationId) {
        try {
            String body = restClient.post()
                    .uri("/api/v1/inventory/reservations/{id}/release", reservationId)
                    .headers(authHeaders())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new InventoryReservationException(502, "Inventory release response was empty");
            }
            JsonNode response = objectMapper.readTree(body);
            return ReleaseOutcome.fromInventoryStatus(response.path("status").asText());
        } catch (RestClientResponseException ex) {
            throw new InventoryReservationException(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (InventoryReservationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InventoryReservationException(
                    502,
                    ex.getMessage() == null ? "Invalid inventory release response" : ex.getMessage()
            );
        }
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

    public enum ReleaseOutcome {
        RELEASED,
        FULFILLED;

        private static ReleaseOutcome fromInventoryStatus(String status) {
            return switch (status) {
                case "RELEASED" -> RELEASED;
                case "FULFILLED" -> FULFILLED;
                default -> throw new InventoryReservationException(
                        502,
                        "Unexpected inventory release status: " + status
                );
            };
        }
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

        public boolean isInsufficientStock() {
            return status == 409 || (getMessage() != null && getMessage().contains("INSUFFICIENT_STOCK"));
        }
    }
}

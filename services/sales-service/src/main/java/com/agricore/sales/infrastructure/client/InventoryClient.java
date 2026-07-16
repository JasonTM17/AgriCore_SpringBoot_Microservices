package com.agricore.sales.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sync HTTP client to inventory-service for saga reserve/release steps.
 */
@Component
public class InventoryClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public InventoryClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${agricore.inventory.base-url}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
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
                    .header("X-Dev-User", "sales-service")
                    .header("X-Dev-Roles", "SALES_STAFF")
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
                .header("X-Dev-User", "sales-service")
                .header("X-Dev-Roles", "SALES_STAFF")
                .retrieve()
                .toBodilessEntity();
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

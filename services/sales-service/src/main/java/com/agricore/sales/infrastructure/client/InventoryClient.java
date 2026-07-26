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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Sync HTTP client to inventory-service for saga reserve/release steps.
 * Uses an internal service credential so recovery jobs do not depend on a caller JWT.
 */
@Component
public class InventoryClient {

    private static final String INTERNAL_BASE_PATH = "/internal/api/v1/inventory";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String internalServiceToken;

    public InventoryClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${agricore.inventory.base-url}") String baseUrl,
            @Value("${agricore.inventory.internal-service-token:}") String internalServiceToken
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.internalServiceToken = internalServiceToken == null ? "" : internalServiceToken.trim();
    }

    public UUID reserve(
            UUID farmId,
            UUID inventoryItemId,
            BigDecimal quantity,
            String referenceId
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("farmId", farmId.toString());
            payload.put("inventoryItemId", inventoryItemId.toString());
            payload.put("quantity", quantity);
            payload.put("referenceType", "SalesOrder");
            payload.put("referenceId", referenceId);

            String body = restClient.post()
                    .uri(INTERNAL_BASE_PATH + "/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(authHeaders())
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(body);
            return UUID.fromString(json.get("id").asText());
        } catch (RestClientResponseException ex) {
            throw responseFailure(ex);
        } catch (InventoryReservationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InventoryReservationException(500, ex.getMessage() == null ? "inventory call failed" : ex.getMessage());
        }
    }

    public ReleaseOutcome release(UUID farmId, UUID reservationId) {
        try {
            String body = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(INTERNAL_BASE_PATH + "/reservations/{id}/release")
                            .queryParam("farmId", farmId)
                            .build(reservationId))
                    .headers(authHeaders())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new InventoryReservationException(502, "Inventory release response was empty");
            }
            JsonNode response = objectMapper.readTree(body);
            return ReleaseOutcome.fromInventoryStatus(response.path("status").asText());
        } catch (RestClientResponseException ex) {
            throw responseFailure(ex);
        } catch (InventoryReservationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InventoryReservationException(
                    502,
                    ex.getMessage() == null ? "Invalid inventory release response" : ex.getMessage()
            );
        }
    }

    public void confirm(UUID farmId, UUID reservationId) {
        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(INTERNAL_BASE_PATH + "/reservations/{id}/confirm")
                            .queryParam("farmId", farmId)
                            .build(reservationId))
                    .headers(authHeaders())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw responseFailure(ex);
        }
    }

    /**
     * Reads Inventory's durable business-reference projection after an
     * ambiguous reserve response. A missing reference is authoritative for
     * this lookup; transport and server failures remain retryable.
     */
    public Optional<ReservationState> findByReference(
            UUID farmId,
            String referenceType,
            String referenceId
    ) {
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(INTERNAL_BASE_PATH + "/reservations/by-reference")
                            .queryParam("farmId", farmId)
                            .queryParam("referenceType", referenceType)
                            .queryParam("referenceId", referenceId)
                            .build())
                    .headers(authHeaders())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new InventoryReservationException(502, "Inventory reservation lookup response was empty");
            }
            JsonNode response = objectMapper.readTree(body);
            UUID id = UUID.fromString(requiredText(response, "id"));
            String status = requiredText(response, "status");
            UUID inventoryItemId = UUID.fromString(requiredText(response, "inventoryItemId"));
            JsonNode quantityNode = response.get("quantity");
            if (quantityNode == null || !quantityNode.isNumber()
                    || quantityNode.decimalValue().signum() <= 0) {
                throw new InventoryReservationException(502, "Inventory response missing valid quantity");
            }
            BigDecimal quantity = quantityNode.decimalValue();
            return Optional.of(new ReservationState(id, inventoryItemId, quantity, status));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw responseFailure(ex);
        } catch (InventoryReservationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InventoryReservationException(
                    502,
                    ex.getMessage() == null ? "Invalid inventory reservation lookup response" : ex.getMessage()
            );
        }
    }

    private InventoryReservationException responseFailure(RestClientResponseException failure) {
        String body = failure.getResponseBodyAsString();
        String errorCode = null;
        try {
            errorCode = objectMapper.readTree(body).path("code").textValue();
        } catch (Exception ignored) {
            // Malformed bodies are retained for diagnostics but never classified by status alone.
        }
        return new InventoryReservationException(failure.getStatusCode().value(), body, errorCode);
    }

    private static String requiredText(JsonNode response, String field) {
        String value = response.path(field).textValue();
        if (value == null || value.isBlank()) {
            throw new InventoryReservationException(502, "Inventory response missing " + field);
        }
        return value;
    }

    private Consumer<HttpHeaders> authHeaders() {
        return headers -> {
            if (internalServiceToken.length() < 32) {
                throw new InventoryReservationException(
                        503,
                        "Inventory internal service token is not configured"
                );
            }
            headers.set("X-Internal-Service-Token", internalServiceToken);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                headers.setBearerAuth(jwtAuth.getToken().getTokenValue());
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

    public record ReservationState(
            UUID id,
            UUID inventoryItemId,
            BigDecimal quantity,
            String status
    ) {
    }

    public static class InventoryReservationException extends RuntimeException {
        private final int status;
        private final String errorCode;

        public InventoryReservationException(int status, String body) {
            this(status, body, "INSUFFICIENT_STOCK".equals(body) ? body : null);
        }

        private InventoryReservationException(int status, String body, String errorCode) {
            super(body);
            this.status = status;
            this.errorCode = errorCode;
        }

        public int getStatus() {
            return status;
        }

        public boolean isInsufficientStock() {
            return "INSUFFICIENT_STOCK".equals(errorCode);
        }
    }
}

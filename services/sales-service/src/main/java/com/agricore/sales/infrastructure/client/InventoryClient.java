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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Sync HTTP client to inventory-service for saga reserve/release steps.
 * Uses an internal service credential so recovery jobs do not depend on a caller JWT.
 */
@Component
public class InventoryClient {

    private static final String INTERNAL_BASE_PATH = "/internal/api/v1/inventory";
    private static final int MAX_ERROR_BODY_INSPECTION_BYTES = 4_096;
    private static final int MAX_SAFE_FAILURE_MESSAGE_LENGTH = 160;
    private static final String DOWNSTREAM_ERROR_CODE = "INVENTORY_DOWNSTREAM_ERROR";
    private static final String INVALID_RESPONSE_CODE = "INVALID_INVENTORY_RESPONSE";
    private static final String UNAVAILABLE_CODE = "INVENTORY_UNAVAILABLE";
    private static final String CONFIGURATION_ERROR_CODE = "INVENTORY_CLIENT_CONFIGURATION_ERROR";
    private static final Set<String> ALLOWED_DOWNSTREAM_ERROR_CODES = Set.of(
            "INSUFFICIENT_STOCK",
            "RESERVATION_REFERENCE_CONFLICT"
    );

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
            if (body == null || body.isBlank()) {
                throw invalidResponse();
            }
            JsonNode json = objectMapper.readTree(body);
            return UUID.fromString(requiredText(json, "id"));
        } catch (RestClientResponseException ex) {
            throw responseFailure(ex);
        } catch (InventoryReservationException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw unavailable();
        } catch (Exception ex) {
            throw invalidResponse();
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
                throw invalidResponse();
            }
            JsonNode response = objectMapper.readTree(body);
            return ReleaseOutcome.fromInventoryStatus(response.path("status").asText());
        } catch (RestClientResponseException ex) {
            throw responseFailure(ex);
        } catch (InventoryReservationException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw unavailable();
        } catch (Exception ex) {
            throw invalidResponse();
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
        } catch (InventoryReservationException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw unavailable();
        } catch (Exception ex) {
            throw invalidResponse();
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
                throw invalidResponse();
            }
            JsonNode response = objectMapper.readTree(body);
            UUID id = UUID.fromString(requiredText(response, "id"));
            String status = requiredText(response, "status");
            UUID inventoryItemId = UUID.fromString(requiredText(response, "inventoryItemId"));
            JsonNode quantityNode = response.get("quantity");
            if (quantityNode == null || !quantityNode.isNumber()
                    || quantityNode.decimalValue().signum() <= 0) {
                throw invalidResponse();
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
        } catch (RestClientException ex) {
            throw unavailable();
        } catch (Exception ex) {
            throw invalidResponse();
        }
    }

    private InventoryReservationException responseFailure(RestClientResponseException failure) {
        String errorCode = DOWNSTREAM_ERROR_CODE;
        byte[] responseBody = failure.getResponseBodyAsByteArray();
        HttpHeaders responseHeaders = failure.getResponseHeaders();
        MediaType contentType = responseHeaders == null ? null : responseHeaders.getContentType();
        if (responseBody != null
                && responseBody.length <= MAX_ERROR_BODY_INSPECTION_BYTES
                && contentType != null
                && MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            try {
                String candidate = objectMapper.readTree(responseBody).path("code").textValue();
                if (ALLOWED_DOWNSTREAM_ERROR_CODES.contains(candidate)) {
                    errorCode = candidate;
                }
            } catch (Exception ignored) {
                // Untrusted or malformed error bodies are intentionally discarded.
            }
        }
        return InventoryReservationException.trusted(failure.getStatusCode().value(), errorCode);
    }

    private static String requiredText(JsonNode response, String field) {
        String value = response.path(field).textValue();
        if (value == null || value.isBlank()) {
            throw invalidResponse();
        }
        return value;
    }

    private static InventoryReservationException invalidResponse() {
        return InventoryReservationException.trusted(502, INVALID_RESPONSE_CODE);
    }

    private static InventoryReservationException unavailable() {
        return InventoryReservationException.trusted(503, UNAVAILABLE_CODE);
    }

    private Consumer<HttpHeaders> authHeaders() {
        return headers -> {
            if (internalServiceToken.length() < 32) {
                throw InventoryReservationException.trusted(
                        503,
                        CONFIGURATION_ERROR_CODE
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
                default -> throw invalidResponse();
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

        public InventoryReservationException(int status, String downstreamCode) {
            this(
                    ALLOWED_DOWNSTREAM_ERROR_CODES.contains(downstreamCode)
                            ? downstreamCode
                            : DOWNSTREAM_ERROR_CODE,
                    normalizeStatus(status)
            );
        }

        private InventoryReservationException(String errorCode, int status) {
            super(safeMessage(status, errorCode));
            this.status = status;
            this.errorCode = errorCode;
        }

        private static InventoryReservationException trusted(int status, String errorCode) {
            return new InventoryReservationException(errorCode, normalizeStatus(status));
        }

        private static int normalizeStatus(int status) {
            return status >= 100 && status <= 599 ? status : 502;
        }

        private static String safeMessage(int status, String errorCode) {
            String message = "Inventory request failed (status=" + status + ", code=" + errorCode + ")";
            return message.length() <= MAX_SAFE_FAILURE_MESSAGE_LENGTH
                    ? message
                    : message.substring(0, MAX_SAFE_FAILURE_MESSAGE_LENGTH);
        }

        public int getStatus() {
            return status;
        }

        public boolean isInsufficientStock() {
            return "INSUFFICIENT_STOCK".equals(errorCode);
        }
    }
}

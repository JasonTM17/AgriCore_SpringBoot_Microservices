package com.agricore.work.infrastructure.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

final class DefaultInventoryStockClient implements InventoryStockClient {

    private static final String STOCK_OUT_PATH = "/internal/api/v1/inventory/stock-out";

    private final RestClient restClient;
    private final InventoryStockResponseDecoder responseDecoder;
    private final boolean securityDevMode;

    DefaultInventoryStockClient(
            RestClient.Builder builder,
            InventoryStockClientProperties properties,
            boolean securityDevMode,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        this.restClient = builder.baseUrl(properties.validatedBaseUri().toString()).build();
        this.responseDecoder = new InventoryStockResponseDecoder(
                objectMapper,
                properties.validatedMaxResponseBytes()
        );
        this.securityDevMode = securityDevMode;
    }

    @Override
    public StockOutResult stockOut(UUID inventoryItemId, BigDecimal quantity, String referenceId) {
        Objects.requireNonNull(inventoryItemId, "inventoryItemId");
        Objects.requireNonNull(quantity, "quantity");
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("referenceId must not be blank");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inventoryItemId", inventoryItemId);
        payload.put("quantity", quantity);
        payload.put("referenceType", "WorkTask");
        payload.put("referenceId", referenceId);

        try {
            InventoryStockResponseDecoder.InventoryItemResponse response = restClient.post()
                    .uri(STOCK_OUT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(authHeaders())
                    .body(payload)
                    .exchange((request, clientResponse) -> {
                        if (!clientResponse.getStatusCode().is2xxSuccessful()) {
                            throw InventoryStockClientException.downstream(
                                    clientResponse.getStatusCode().value()
                            );
                        }
                        return responseDecoder.decode(clientResponse);
                    });
            if (response == null
                    || !inventoryItemId.equals(response.id())
                    || response.warehouseId() == null
                    || response.unit() == null
                    || response.unit().isBlank()
                    || response.unit().length() > 16
                    || response.onHandQuantity() == null
                    || response.reservedQuantity() == null
                    || response.availableQuantity() == null
                    || response.onHandQuantity().signum() < 0
                    || response.reservedQuantity().signum() < 0
                    || response.availableQuantity().signum() < 0
                    || response.onHandQuantity().subtract(response.reservedQuantity())
                            .compareTo(response.availableQuantity()) != 0
                    || response.version() < 0) {
                throw InventoryStockClientException.unavailable(
                        new IllegalStateException("Inventory stock-out returned inconsistent item state")
                );
            }
            return new StockOutResult(response.id(), response.unit());
        } catch (InventoryStockClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw InventoryStockClientException.unavailable(exception);
        }
    }

    private Consumer<HttpHeaders> authHeaders() {
        Authentication authentication = currentAuthentication();
        return headers -> {
            if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                headers.setBearerAuth(jwtAuthentication.getToken().getTokenValue());
                return;
            }
            if (!securityDevMode) {
                throw InventoryStockClientException.unavailable(
                        new IllegalStateException("No forwardable caller token")
                );
            }
            List<String> roles = authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .map(authority -> authority.substring("ROLE_".length()))
                    .distinct()
                    .sorted()
                    .toList();
            if (roles.isEmpty()) {
                throw InventoryStockClientException.unavailable(
                        new IllegalStateException("No forwardable caller role")
                );
            }
            headers.set("X-Dev-User", authentication.getName());
            headers.set("X-Dev-Roles", String.join(",", roles));
        };
    }

    private static Authentication currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw InventoryStockClientException.unavailable(
                    new IllegalStateException("Authenticated caller is required")
            );
        }
        return authentication;
    }
}

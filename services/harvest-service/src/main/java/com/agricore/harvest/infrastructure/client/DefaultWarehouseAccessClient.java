package com.agricore.harvest.infrastructure.client;

import com.agricore.harvest.domain.exception.HarvestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.function.Consumer;

final class DefaultWarehouseAccessClient implements WarehouseAccessClient {

    private static final String WAREHOUSE_SCOPE_PATH =
            "/internal/api/v1/inventory/warehouses/{warehouseId}/scope";
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxResponseBytes;
    private final WarehouseAccessProperties properties;

    DefaultWarehouseAccessClient(
            RestClient.Builder builder,
            WarehouseAccessProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.validatedBaseUri().toString()).build();
        this.objectMapper = objectMapper;
        this.maxResponseBytes = properties.validatedMaxResponseBytes();
    }

    @Override
    public void requireWarehouse(UUID warehouseId, UUID farmId) {
        if (warehouseId == null || farmId == null) {
            throw unavailable();
        }
        try {
            WarehouseScope scope = restClient.get()
                    .uri(WAREHOUSE_SCOPE_PATH, warehouseId)
                    .headers(serviceHeaders())
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            throw notFound();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw unavailable();
                        }
                        return decode(
                                response.getBody(),
                                response.getHeaders().getContentLength()
                        );
                    });
            if (scope == null || !warehouseId.equals(scope.warehouseId())) {
                throw unavailable();
            }
            if (!farmId.equals(scope.farmId())) {
                throw notFound();
            }
        } catch (HarvestException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private WarehouseScope decode(InputStream input, long contentLength) {
        if (input == null || contentLength > maxResponseBytes) {
            throw unavailable();
        }
        try {
            byte[] body = input.readNBytes(maxResponseBytes + 1);
            if (body.length == 0 || body.length > maxResponseBytes) {
                throw unavailable();
            }
            JsonNode response = objectMapper.readTree(body);
            if (response == null
                    || !response.isObject()
                    || response.size() != 2
                    || !response.has("warehouseId")
                    || !response.has("farmId")) {
                throw unavailable();
            }
            return new WarehouseScope(
                    requiredUuid(response, "warehouseId"),
                    requiredUuid(response, "farmId")
            );
        } catch (HarvestException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private Consumer<HttpHeaders> serviceHeaders() {
        String serviceToken = properties.getInternalServiceToken();
        if (serviceToken.isBlank()) {
            throw unavailable();
        }
        return headers -> headers.set(INTERNAL_SERVICE_TOKEN_HEADER, serviceToken);
    }

    private static UUID requiredUuid(JsonNode response, String field) {
        JsonNode value = response.get(field);
        if (value == null || !value.isTextual()) {
            throw unavailable();
        }
        return UUID.fromString(value.textValue());
    }

    private static HarvestException notFound() {
        return new HarvestException("WAREHOUSE_NOT_FOUND", "Warehouse not found", 404);
    }

    private static HarvestException unavailable() {
        return new HarvestException(
                "WAREHOUSE_ACCESS_UNAVAILABLE",
                "Warehouse access is temporarily unavailable",
                503
        );
    }

    private record WarehouseScope(UUID warehouseId, UUID farmId) {
    }
}

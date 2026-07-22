package com.agricore.work.infrastructure.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

final class InventoryStockResponseDecoder {

    private final ObjectReader reader;
    private final int maxResponseBytes;

    InventoryStockResponseDecoder(ObjectMapper objectMapper, int maxResponseBytes) {
        ObjectMapper strictMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.reader = strictMapper.readerFor(InventoryItemResponse.class);
        this.maxResponseBytes = maxResponseBytes;
    }

    InventoryItemResponse decode(ClientHttpResponse response) {
        try {
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                throw invalidResponse("unexpected content type");
            }
            long contentLength = response.getHeaders().getContentLength();
            if (contentLength > maxResponseBytes) {
                throw invalidResponse("response exceeds configured byte limit");
            }
            byte[] body = response.getBody().readNBytes(maxResponseBytes + 1);
            if (body.length > maxResponseBytes) {
                throw invalidResponse("response exceeds configured byte limit");
            }
            InventoryItemResponse result = reader.readValue(body);
            if (result == null) {
                throw invalidResponse("empty response body");
            }
            return result;
        } catch (InventoryStockClientException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw InventoryStockClientException.unavailable(exception);
        }
    }

    private static InventoryStockClientException invalidResponse(String reason) {
        return InventoryStockClientException.unavailable(
                new IllegalStateException("Invalid inventory stock-out response: " + reason)
        );
    }

    record InventoryItemResponse(
            UUID id,
            UUID warehouseId,
            String sku,
            String name,
            String itemType,
            String unit,
            BigDecimal onHandQuantity,
            BigDecimal reservedQuantity,
            BigDecimal availableQuantity,
            long version
    ) {
    }
}

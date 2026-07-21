package com.agricore.assistant.infrastructure.tool.farm;

import com.agricore.assistant.application.port.ToolCollectionException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class FarmToolResponseDecoder {

    private final ObjectMapper objectMapper;
    private final int maxResponseBytes;

    FarmToolResponseDecoder(ObjectMapper objectMapper, int maxResponseBytes) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.maxResponseBytes = maxResponseBytes;
    }

    FarmDetails decodeFarm(ClientHttpResponse response) {
        return decode(response, FarmDetails.class);
    }

    PlotPage decodePlots(ClientHttpResponse response) {
        return decode(response, PlotPage.class);
    }

    private <T> T decode(ClientHttpResponse response, Class<T> type) {
        try {
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                throw ToolCollectionException.responseInvalid();
            }
            byte[] payload = response.getBody().readNBytes(maxResponseBytes + 1);
            if (payload.length == 0 || payload.length > maxResponseBytes) {
                throw ToolCollectionException.responseInvalid();
            }
            return objectMapper.readValue(payload, type);
        } catch (ToolCollectionException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw ToolCollectionException.responseInvalid();
        }
    }

    record FarmDetails(
            UUID id,
            String code,
            String name,
            String address,
            String province,
            BigDecimal totalAreaHa,
            Double latitude,
            Double longitude,
            String status,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    record PlotDetails(
            UUID id,
            UUID farmId,
            UUID areaId,
            String code,
            String name,
            BigDecimal areaInHectares,
            String soilType,
            String status,
            Double latitude,
            Double longitude,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
    }

    record PlotPage(
            List<PlotDetails> content,
            Integer page,
            Integer size,
            Long totalElements,
            Integer totalPages,
            Boolean first,
            Boolean last
    ) {
    }
}

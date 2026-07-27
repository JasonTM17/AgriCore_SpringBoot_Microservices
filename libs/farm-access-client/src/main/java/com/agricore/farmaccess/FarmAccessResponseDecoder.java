package com.agricore.farmaccess;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

final class FarmAccessResponseDecoder {

    private final ObjectReader reader;
    private final int maxResponseBytes;

    FarmAccessResponseDecoder(ObjectMapper objectMapper, int maxResponseBytes) {
        ObjectMapper strictMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.reader = strictMapper.readerFor(FarmResourceAccess.class);
        this.maxResponseBytes = maxResponseBytes;
    }

    FarmResourceAccess decode(ClientHttpResponse response) {
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
            FarmResourceAccess access = reader.readValue(body);
            if (access == null) {
                throw invalidResponse("empty response body");
            }
            return access;
        } catch (FarmAccessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw FarmAccessException.unavailable(ex);
        }
    }

    private static FarmAccessException invalidResponse(String reason) {
        return FarmAccessException.unavailable(
                new IllegalStateException("Invalid farm access response: " + reason)
        );
    }
}

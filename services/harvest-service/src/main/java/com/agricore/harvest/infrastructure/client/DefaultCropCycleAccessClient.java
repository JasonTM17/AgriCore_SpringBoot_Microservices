package com.agricore.harvest.infrastructure.client;

import com.agricore.harvest.domain.exception.HarvestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.function.Consumer;

final class DefaultCropCycleAccessClient implements CropCycleAccessClient {

    private static final String CYCLE_PATH = "/api/v1/crop-cycles/{cropCycleId}";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxResponseBytes;

    DefaultCropCycleAccessClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            int maxResponseBytes
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public void requireCycle(UUID cropCycleId, UUID farmId, UUID plotId) {
        try {
            CycleScope scope = restClient.get()
                    .uri(CYCLE_PATH, cropCycleId)
                    .headers(bearerHeaders())
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 403) {
                            throw error(
                                    "CROP_CYCLE_ACCESS_DENIED",
                                    "Crop cycle access denied",
                                    403
                            );
                        }
                        if (status == 404) {
                            throw notFound();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw unavailable();
                        }
                        return decode(response.getBody(), response.getHeaders().getContentLength());
                    });
            if (scope == null
                    || !cropCycleId.equals(scope.id())
                    || !farmId.equals(scope.farmId())
                    || !plotId.equals(scope.plotId())) {
                throw notFound();
            }
        } catch (HarvestException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private CycleScope decode(InputStream input, long contentLength) {
        if (input == null || contentLength > maxResponseBytes) {
            throw unavailable();
        }
        try {
            byte[] body = input.readNBytes(maxResponseBytes + 1);
            if (body.length == 0 || body.length > maxResponseBytes) {
                throw unavailable();
            }
            JsonNode response = objectMapper.readTree(body);
            return new CycleScope(
                    requiredUuid(response, "id"),
                    requiredUuid(response, "farmId"),
                    requiredUuid(response, "plotId")
            );
        } catch (HarvestException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private static UUID requiredUuid(JsonNode response, String field) {
        JsonNode value = response == null ? null : response.get(field);
        if (value == null || !value.isTextual()) {
            throw unavailable();
        }
        return UUID.fromString(value.textValue());
    }

    private static Consumer<HttpHeaders> bearerHeaders() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            throw unavailable();
        }
        return headers -> headers.setBearerAuth(jwtAuthentication.getToken().getTokenValue());
    }

    private static HarvestException notFound() {
        return error("CROP_CYCLE_NOT_FOUND", "Crop cycle not found", 404);
    }

    private static HarvestException unavailable() {
        return error(
                "CROP_CYCLE_ACCESS_UNAVAILABLE",
                "Crop cycle access is temporarily unavailable",
                503
        );
    }

    private static HarvestException error(String code, String message, int status) {
        return new HarvestException(code, message, status);
    }

    private record CycleScope(UUID id, UUID farmId, UUID plotId) {
    }
}

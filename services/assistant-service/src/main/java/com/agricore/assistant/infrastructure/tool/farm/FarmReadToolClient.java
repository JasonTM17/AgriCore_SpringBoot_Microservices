package com.agricore.assistant.infrastructure.tool.farm;

import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.port.ToolCollectionException;
import com.agricore.assistant.infrastructure.configuration.AssistantToolProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class FarmReadToolClient {

    private static final String FARM_PATH = "/api/v1/farms/{farmId}";
    private static final String PLOTS_PATH = "/api/v1/farms/{farmId}/plots?page=0&size={size}";

    private final RestClient restClient;
    private final FarmToolResponseDecoder decoder;
    private final FarmToolEvidenceProjector projector = new FarmToolEvidenceProjector();
    private final int maximumPlots;
    private final boolean securityDevMode;

    public FarmReadToolClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            AssistantToolProperties properties,
            boolean securityDevMode
    ) {
        this.restClient = builder.baseUrl(properties.validatedFarmBaseUri().toString()).build();
        this.decoder = new FarmToolResponseDecoder(objectMapper, properties.validatedMaxResponseBytes());
        this.maximumPlots = properties.validatedMaxPlots();
        this.securityDevMode = securityDevMode;
    }

    public ToolEvidenceSnapshot collect(UUID farmId) {
        if (farmId == null) {
            throw ToolCollectionException.scopeUnavailable();
        }
        String correlationId = UUID.randomUUID().toString();
        Consumer<HttpHeaders> authorization = authorizationHeaders(correlationId);
        var farm = get(FARM_PATH, authorization, decoder::decodeFarm, farmId);
        var plots = get(PLOTS_PATH, authorization, decoder::decodePlots, farmId, maximumPlots);
        return projector.project(farmId, farm, plots, maximumPlots);
    }

    private <T> T get(
            String path,
            Consumer<HttpHeaders> headers,
            ResponseReader<T> responseReader,
            Object... uriVariables
    ) {
        try {
            return restClient.get()
                    .uri(path, uriVariables)
                    .headers(headers)
                    .exchange((request, response) -> {
                        requireSuccess(response.getStatusCode());
                        return responseReader.read(response);
                    });
        } catch (ToolCollectionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ToolCollectionException.dependencyUnavailable();
        }
    }

    private Consumer<HttpHeaders> authorizationHeaders(String correlationId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw ToolCollectionException.authorizationUnavailable();
        }
        return headers -> {
            headers.set("X-Correlation-ID", correlationId);
            if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                headers.setBearerAuth(jwtAuthentication.getToken().getTokenValue());
                return;
            }
            if (!securityDevMode) {
                throw ToolCollectionException.authorizationUnavailable();
            }
            List<String> roles = authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .map(authority -> authority.substring("ROLE_".length()))
                    .distinct()
                    .sorted()
                    .toList();
            if (roles.isEmpty()) {
                throw ToolCollectionException.authorizationUnavailable();
            }
            headers.set("X-Dev-User", authentication.getName());
            headers.set("X-Dev-Roles", String.join(",", roles));
        };
    }

    private static void requireSuccess(HttpStatusCode status) {
        int value = status.value();
        if (value == 401 || value == 403 || value == 404) {
            throw ToolCollectionException.scopeUnavailable();
        }
        if (value == 429) {
            throw ToolCollectionException.rateLimited();
        }
        if (!status.is2xxSuccessful()) {
            throw ToolCollectionException.dependencyUnavailable();
        }
    }

    @FunctionalInterface
    private interface ResponseReader<T> {
        T read(org.springframework.http.client.ClientHttpResponse response);
    }
}

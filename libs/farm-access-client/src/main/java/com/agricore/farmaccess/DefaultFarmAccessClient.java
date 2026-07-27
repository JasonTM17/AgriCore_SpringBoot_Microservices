package com.agricore.farmaccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class DefaultFarmAccessClient implements FarmAccessClient {

    private static final String FARM_PATH = "/internal/api/v1/farm-access/farms/{farmId}";
    private static final String PLOT_PATH = "/internal/api/v1/farm-access/plots/{plotId}";
    private static final String FARM_PLOT_PATH =
            "/internal/api/v1/farm-access/farms/{farmId}/plots/{plotId}";

    private final RestClient restClient;
    private final FarmAccessResponseDecoder responseDecoder;
    private final boolean securityDevMode;

    public DefaultFarmAccessClient(
            RestClient.Builder builder,
            FarmAccessProperties properties,
            boolean securityDevMode
    ) {
        this(builder, properties, securityDevMode, JsonMapper.builder().findAndAddModules().build());
    }

    DefaultFarmAccessClient(
            RestClient.Builder builder,
            FarmAccessProperties properties,
            boolean securityDevMode,
            ObjectMapper objectMapper
    ) {
        this.restClient = builder.baseUrl(properties.validatedBaseUri().toString()).build();
        this.responseDecoder = new FarmAccessResponseDecoder(
                objectMapper,
                properties.validatedMaxResponseBytes()
        );
        this.securityDevMode = securityDevMode;
    }

    @Override
    public FarmResourceAccess requireFarm(UUID farmId) {
        Objects.requireNonNull(farmId, "farmId");
        FarmResourceAccess access = get(FARM_PATH, farmId);
        if (!farmId.equals(access.farmId()) || access.plotId() != null) {
            throw FarmAccessException.unavailable();
        }
        return access;
    }

    @Override
    public FarmResourceAccess requirePlot(UUID plotId) {
        Objects.requireNonNull(plotId, "plotId");
        FarmResourceAccess access = get(PLOT_PATH, plotId);
        if (access.farmId() == null || !plotId.equals(access.plotId())) {
            throw FarmAccessException.unavailable();
        }
        return access;
    }

    @Override
    public FarmResourceAccess requireFarmPlot(UUID farmId, UUID plotId) {
        Objects.requireNonNull(farmId, "farmId");
        Objects.requireNonNull(plotId, "plotId");
        FarmResourceAccess access = get(FARM_PLOT_PATH, farmId, plotId);
        if (!farmId.equals(access.farmId()) || !plotId.equals(access.plotId())) {
            throw FarmAccessException.unavailable();
        }
        return access;
    }

    @Override
    public boolean isSystemAdmin() {
        Authentication authentication = currentAuthentication();
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SYSTEM_ADMIN".equals(authority.getAuthority()));
    }

    private FarmResourceAccess get(String path, Object... uriVariables) {
        try {
            FarmResourceAccess access = restClient.get()
                    .uri(path, uriVariables)
                    .headers(authHeaders())
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 403) {
                            throw FarmAccessException.denied();
                        }
                        if (status == 404) {
                            throw FarmAccessException.notFound();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw FarmAccessException.unavailable(new IllegalStateException(
                                    "Farm access service returned HTTP " + status
                            ));
                        }
                        return responseDecoder.decode(response);
                    });
            if (access == null) {
                throw FarmAccessException.unavailable();
            }
            return access;
        } catch (FarmAccessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw FarmAccessException.unavailable(ex);
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
                throw FarmAccessException.unavailable();
            }
            List<String> roles = authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .map(authority -> authority.substring("ROLE_".length()))
                    .distinct()
                    .sorted()
                    .toList();
            if (roles.isEmpty()) {
                throw FarmAccessException.unavailable();
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
            throw FarmAccessException.unavailable();
        }
        return authentication;
    }
}

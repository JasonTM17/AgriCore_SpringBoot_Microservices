package com.agricore.farmaccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DefaultFarmAccessClientTest {

    private static final String BASE_URL = "https://farm-service";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requirePlot_forwardsCallerJwtAndValidatesResponseIdentity() {
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        setJwtAuthentication("signed-access-token");
        TestClient fixture = client(false);
        fixture.server().expect(once(), requestTo(BASE_URL + "/internal/api/v1/farm-access/plots/" + plotId))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer signed-access-token"))
                .andRespond(withSuccess(
                        "{\"farmId\":\"%s\",\"plotId\":\"%s\"}".formatted(farmId, plotId),
                        MediaType.APPLICATION_JSON
                ));

        FarmResourceAccess access = fixture.client().requirePlot(plotId);

        assertThat(access.farmId()).isEqualTo(farmId);
        assertThat(access.plotId()).isEqualTo(plotId);
        fixture.server().verify();
    }

    @Test
    void requireFarm_forwardsAuthenticatedDevCallerWithoutServiceSubstitution() {
        UUID farmId = UUID.randomUUID();
        setDevAuthentication("manager-a", "ROLE_FARM_MANAGER", "ROLE_AGRONOMIST");
        TestClient fixture = client(true);
        fixture.server().expect(once(), requestTo(BASE_URL + "/internal/api/v1/farm-access/farms/" + farmId))
                .andExpect(header("X-Dev-User", "manager-a"))
                .andExpect(header("X-Dev-Roles", "AGRONOMIST,FARM_MANAGER"))
                .andRespond(withSuccess(
                        "{\"farmId\":\"%s\",\"plotId\":null}".formatted(farmId),
                        MediaType.APPLICATION_JSON
                ));

        assertThat(fixture.client().requireFarm(farmId).farmId()).isEqualTo(farmId);
        fixture.server().verify();
    }

    @Test
    void requirePlot_preservesMaskedNotFound() {
        UUID plotId = UUID.randomUUID();
        setDevAuthentication("manager-b", "ROLE_FARM_MANAGER");
        TestClient fixture = client(true);
        fixture.server().expect(once(), requestTo(BASE_URL + "/internal/api/v1/farm-access/plots/" + plotId))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatExceptionOfType(FarmAccessException.class)
                .isThrownBy(() -> fixture.client().requirePlot(plotId))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("FARM_RESOURCE_NOT_FOUND");
                });
    }

    @Test
    void requirePlot_mapsFarmServiceFailureToServiceUnavailable() {
        UUID plotId = UUID.randomUUID();
        setDevAuthentication("manager-c", "ROLE_FARM_MANAGER");
        TestClient fixture = client(true);
        fixture.server().expect(once(), requestTo(BASE_URL + "/internal/api/v1/farm-access/plots/" + plotId))
                .andRespond(withServerError());

        assertThatExceptionOfType(FarmAccessException.class)
                .isThrownBy(() -> fixture.client().requirePlot(plotId))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(503);
                    assertThat(ex.getCode()).isEqualTo("FARM_ACCESS_UNAVAILABLE");
                });
    }

    @Test
    void requireFarm_preservesForbidden() {
        UUID farmId = UUID.randomUUID();
        setDevAuthentication("worker-a", "ROLE_FIELD_WORKER");
        TestClient fixture = client(true);
        fixture.server().expect(once(), requestTo(BASE_URL + "/internal/api/v1/farm-access/farms/" + farmId))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

        assertThatExceptionOfType(FarmAccessException.class)
                .isThrownBy(() -> fixture.client().requireFarm(farmId))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(403);
                    assertThat(ex.getCode()).isEqualTo("FARM_ACCESS_DENIED");
                });
    }

    @Test
    void requireFarmPlot_rejectsMismatchedAuthoritativeResponse() {
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        setDevAuthentication("manager-d", "ROLE_FARM_MANAGER");
        TestClient fixture = client(true);
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/api/v1/farm-access/farms/" + farmId + "/plots/" + plotId))
                .andRespond(withSuccess(
                        "{\"farmId\":\"%s\",\"plotId\":\"%s\"}".formatted(UUID.randomUUID(), plotId),
                        MediaType.APPLICATION_JSON
                ));

        assertThatExceptionOfType(FarmAccessException.class)
                .isThrownBy(() -> fixture.client().requireFarmPlot(farmId, plotId))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(503);
                    assertThat(ex.getCode()).isEqualTo("FARM_ACCESS_UNAVAILABLE");
                });
    }

    @Test
    void isSystemAdmin_usesAuthenticatedAuthorities() {
        setDevAuthentication("admin-a", "ROLE_SYSTEM_ADMIN");

        assertThat(client(true).client().isSystemAdmin()).isTrue();
    }

    private static TestClient client(boolean devMode) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FarmAccessProperties properties = new FarmAccessProperties();
        properties.setBaseUrl(BASE_URL);
        return new TestClient(new DefaultFarmAccessClient(builder, properties, devMode), server);
    }

    private static void setJwtAuthentication(String tokenValue) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject("user-a")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER")),
                "user-a"
        ));
    }

    private static void setDevAuthentication(String subject, String... roles) {
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(subject, null, authorities)
        );
    }

    private record TestClient(DefaultFarmAccessClient client, MockRestServiceServer server) {
    }
}

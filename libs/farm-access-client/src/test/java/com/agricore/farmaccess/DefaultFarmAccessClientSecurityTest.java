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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DefaultFarmAccessClientSecurityTest {

    private static final String BASE_URL = "https://farm-service";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void constructor_rejectsDestinationOutsideConfiguredAllowlist() {
        FarmAccessProperties properties = properties("https://attacker.example");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DefaultFarmAccessClient(
                        RestClient.builder(), properties, false));
    }

    @Test
    void constructor_rejectsPlainHttpForNonLoopbackDestinationByDefault() {
        FarmAccessProperties properties = properties("http://farm-service");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DefaultFarmAccessClient(
                        RestClient.builder(), properties, false));
    }

    @Test
    void requireFarm_rejectsResponseWithUnknownFields() {
        UUID farmId = UUID.randomUUID();
        setJwtAuthentication();
        TestClient fixture = client(false);
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/api/v1/farm-access/farms/" + farmId))
                .andRespond(withSuccess(
                        "{\"farmId\":\"%s\",\"plotId\":null,\"unexpected\":true}"
                                .formatted(farmId),
                        MediaType.APPLICATION_JSON
                ));

        assertUnavailable(() -> fixture.client().requireFarm(farmId));
    }

    @Test
    void requireFarm_rejectsResponseMissingRequiredNullablePlotId() {
        UUID farmId = UUID.randomUUID();
        setJwtAuthentication();
        TestClient fixture = client(false);
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/api/v1/farm-access/farms/" + farmId))
                .andRespond(withSuccess(
                        "{\"farmId\":\"%s\"}".formatted(farmId),
                        MediaType.APPLICATION_JSON
                ));

        assertUnavailable(() -> fixture.client().requireFarm(farmId));
    }

    @Test
    void requireFarm_rejectsResponseLargerThanDefaultLimit() {
        UUID farmId = UUID.randomUUID();
        setJwtAuthentication();
        TestClient fixture = client(false);
        String response = "{\"farmId\":\"%s\",\"plotId\":null}".formatted(farmId)
                + " ".repeat(5_000);
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/api/v1/farm-access/farms/" + farmId))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertUnavailable(() -> fixture.client().requireFarm(farmId));
    }

    @Test
    void requirePlot_mapsUnauthorizedFarmServiceResponseToUnavailable() {
        UUID plotId = UUID.randomUUID();
        setJwtAuthentication();
        TestClient fixture = client(false);
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/api/v1/farm-access/plots/" + plotId))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));

        assertUnavailable(() -> fixture.client().requirePlot(plotId));
    }

    @Test
    void requireFarm_mapsMalformedResponseHeadersToUnavailable() {
        UUID farmId = UUID.randomUUID();
        setJwtAuthentication();
        TestClient fixture = client(false);
        fixture.server().expect(once(), requestTo(
                        BASE_URL + "/internal/api/v1/farm-access/farms/" + farmId))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "not a valid media type (")
                        .body("{\"farmId\":\"%s\",\"plotId\":null}".formatted(farmId)));

        assertUnavailable(() -> fixture.client().requireFarm(farmId));
    }

    @Test
    void requireFarm_failsClosedWithoutAuthenticatedRequestContext() {
        TestClient fixture = client(false);

        assertUnavailable(() -> fixture.client().requireFarm(UUID.randomUUID()));
    }

    @Test
    void requireFarm_doesNotForwardDevIdentityWhenDevModeIsDisabled() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "manager-a",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER"))
                )
        );
        TestClient fixture = client(false);

        assertUnavailable(() -> fixture.client().requireFarm(UUID.randomUUID()));
    }

    private static void assertUnavailable(Runnable operation) {
        assertThatExceptionOfType(FarmAccessException.class)
                .isThrownBy(operation::run)
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(503);
                    assertThat(ex.getCode()).isEqualTo("FARM_ACCESS_UNAVAILABLE");
                });
    }

    private static TestClient client(boolean devMode) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FarmAccessProperties properties = properties(BASE_URL);
        return new TestClient(new DefaultFarmAccessClient(builder, properties, devMode), server);
    }

    private static FarmAccessProperties properties(String baseUrl) {
        FarmAccessProperties properties = new FarmAccessProperties();
        properties.setBaseUrl(baseUrl);
        return properties;
    }

    private static void setJwtAuthentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("signed-access-token")
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

    private record TestClient(DefaultFarmAccessClient client, MockRestServiceServer server) {
    }
}

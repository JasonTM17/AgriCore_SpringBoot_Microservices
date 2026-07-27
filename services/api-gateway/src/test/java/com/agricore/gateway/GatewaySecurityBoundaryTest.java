package com.agricore.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway is the platform's authentication boundary, and nothing exercised it: the only test
 * loaded the context, and the test profile sets {@code jwt-enabled: false}, so even that ran with
 * security switched off. These tests turn JWT enforcement on.
 *
 * <p>Every assertion here is deliberately about a decision the gateway reaches <em>without
 * contacting an upstream</em>, because upstream reachability is not something a unit of CI should
 * depend on. Rejections happen in the security filter chain before routing, and a permitted path
 * with no matching route is answered 404 by the gateway itself — so permitAll is proved by the
 * absence of a 401 on an unrouted path rather than by proxying somewhere.
 *
 * <p>An earlier draft asserted on {@code /public/api/v1/traceability/...}, which does route to a
 * real service. That made the outcome depend on whether anything happened to be listening on the
 * upstream port locally, which is exactly the flakiness this arrangement avoids.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "agricore.security.jwt-enabled=true",
                "agricore.security.jwk-set-uri=http://localhost:59999/.well-known/jwks.json",
                "agricore.gateway.client-ip.signing-secret=test-client-ip-signing-secret"
        }
)
class GatewaySecurityBoundaryTest {

    @Autowired
    private WebTestClient client;

    private int statusOf(String path) {
        return client.get().uri(path).exchange().returnResult(Void.class).getStatus().value();
    }

    @Test
    void protectedRouteWithoutTokenIsUnauthorized() {
        client.get().uri("/api/v1/farms").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteWithGarbageBearerTokenIsUnauthorized() {
        client.get().uri("/api/v1/farms")
                .header("Authorization", "Bearer not-a-jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unsafeAmbientCookieRequestWithoutBearerFailsClosed() {
        client.post().uri("/api/v1/farms")
                .cookie("ingress-affinity", "value")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);
    }

    @Test
    void ambientCookieDoesNotOverrideExplicitBearerAuthentication() {
        client.post().uri("/api/v1/farms")
                .cookie("ingress-affinity", "value")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);
    }

    /**
     * Every upstream prefix the gateway routes must sit behind authentication. Rejection happens
     * before routing, so no upstream is contacted and the result does not depend on what is running.
     */
    @Test
    void everyUpstreamPrefixIsBehindAuthentication() {
        String[] protectedPrefixes = {
                "/api/v1/farms", "/api/v1/plots", "/api/v1/crops", "/api/v1/crop-cycles",
                "/api/v1/work-tasks", "/api/v1/inventory/items", "/api/v1/harvests",
                "/api/v1/iot/devices", "/api/v1/sales/orders", "/api/v1/notifications",
                "/api/v1/users/me", "/api/v1/admin/users", "/api/v1/traceability/batches"
        };
        for (String path : protectedPrefixes) {
            assertThat(statusOf(path))
                    .as("%s must require authentication at the gateway", path)
                    .isEqualTo(401);
        }
    }

    /**
     * The public QR prefix must not be behind authentication. Uses a path under {@code /public/}
     * that matches no route: security permits it, then the gateway answers 404 itself. A 401 here
     * would mean the permitAll rule had stopped covering the prefix.
     */
    @Test
    void publicPrefixIsNotBehindAuthentication() {
        assertThat(statusOf("/public/unrouted-probe"))
                .as("/public/** must be permitted by security, not rejected")
                .isEqualTo(404);
    }

    @Test
    void healthIsOpenForProbes() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    /**
     * Regression guard. The gateway actuator endpoint lists every internal upstream URI and was
     * previously exposed, reachable with any valid token. It must stay unexposed.
     */
    @Test
    void gatewayActuatorEndpointIsNotExposed() {
        assertThat(statusOf("/actuator/gateway/routes"))
                .as("/actuator/gateway must not be exposed")
                .isNotEqualTo(200);
    }
}

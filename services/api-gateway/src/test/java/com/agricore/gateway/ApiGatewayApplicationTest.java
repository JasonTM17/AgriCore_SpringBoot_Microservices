package com.agricore.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayApplicationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitions;

    @Autowired
    private RouteLocator routes;

    @Test
    void contextLoads() {
    }

    @Test
    void assistantRouteUsesLongLivedStreamingTimeout() {
        List<RouteDefinition> routes = routeDefinitions.getRouteDefinitions().collectList().block();

        assertThat(routes).isNotNull();
        RouteDefinition assistantRoute = routes.stream()
                .filter(route -> "assistant-service".equals(route.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(assistantRoute.getUri().toString()).isEqualTo("http://localhost:8093");
        assertThat(assistantRoute.getPredicates()).anyMatch(predicate ->
                predicate.getName().equals("Path")
                        && predicate.getArgs().values().stream()
                        .anyMatch(value -> value.contains("/api/v1/assistant/**")));
        assertThat(assistantRoute.getMetadata()).containsEntry("response-timeout", "30m");
    }

    @Test
    void farmRouteMatchesEnterpriseCollectionAndItemPaths() {
        Route farmRoute = routes.getRoutes()
                .filter(route -> "farm-service".equals(route.getId()))
                .blockFirst();

        assertThat(farmRoute).isNotNull();
        assertThat(matches(farmRoute, "/api/v1/enterprises")).isTrue();
        assertThat(matches(
                farmRoute,
                "/api/v1/enterprises/8fa2a7e2-91a5-4d8c-a346-a1534ba237f1"
        )).isTrue();
    }

    private static boolean matches(Route route, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build()
        );
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block());
    }
}

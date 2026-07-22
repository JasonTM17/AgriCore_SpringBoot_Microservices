package com.agricore.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayApplicationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitions;

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
}

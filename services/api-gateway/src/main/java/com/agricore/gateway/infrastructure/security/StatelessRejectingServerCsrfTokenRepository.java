package com.agricore.gateway.infrastructure.security;

import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Keeps the gateway stateless: cookie-backed mutations outside Identity are rejected instead of
 * creating a gateway-wide browser token contract.
 */
final class StatelessRejectingServerCsrfTokenRepository implements ServerCsrfTokenRepository {

    static final String HEADER_NAME = "X-AGRICORE-GATEWAY-XSRF-TOKEN";
    static final String PARAMETER_NAME = "_agricore_gateway_xsrf";

    @Override
    public Mono<CsrfToken> generateToken(ServerWebExchange exchange) {
        return Mono.fromSupplier(
                () -> new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, UUID.randomUUID().toString())
        );
    }

    @Override
    public Mono<Void> saveToken(ServerWebExchange exchange, CsrfToken token) {
        // Gateway deliberately persists no browser credential or CSRF state.
        return Mono.empty();
    }

    @Override
    public Mono<CsrfToken> loadToken(ServerWebExchange exchange) {
        return Mono.empty();
    }
}

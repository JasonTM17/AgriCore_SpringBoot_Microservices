package com.agricore.gateway.infrastructure.security;

import com.agricore.security.AgricoreJwtValidators;
import com.agricore.security.JwtRolesConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Gateway-level JWT validation against Identity Service JWKS (issuer + audience).
 * Public auth, JWKS, actuator, and public traceability remain open.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private static final String IDENTITY_AUTH_PATH = "/api/v1/auth";
    private static final String BEARER_AUTHORIZATION_PREFIX = "Bearer ";
    private static final Set<HttpMethod> SAFE_METHODS = Set.of(
            HttpMethod.GET,
            HttpMethod.HEAD,
            HttpMethod.OPTIONS,
            HttpMethod.TRACE
    );
    private static final ServerCsrfTokenRepository REJECTING_CSRF_TOKEN_REPOSITORY =
            new StatelessRejectingServerCsrfTokenRepository();

    @Value("${agricore.security.jwk-set-uri:http://localhost:8081/.well-known/jwks.json}")
    private String jwkSetUri;

    @Value("${agricore.security.jwt-enabled:true}")
    private boolean jwtEnabled;

    @Value("${agricore.security.issuer:https://agricore.local/identity}")
    private String issuer;

    @Value("${agricore.security.audience:agricore-api}")
    private String audience;

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        // Gateway accepts explicit bearer credentials for application routes and delegates cookie
        // authentication to Identity. Unsafe ambient-cookie requests without bearer credentials
        // fail closed instead of minting a gateway-wide browser CSRF token contract.
        http.csrf(csrf -> csrf
                        .csrfTokenRepository(REJECTING_CSRF_TOKEN_REPOSITORY)
                        .requireCsrfProtectionMatcher(GatewaySecurityConfig::requiresCookieConditionedCsrfProtection))
                .cors(Customizer.withDefaults());

        if (!jwtEnabled) {
            http.authorizeExchange(ex -> ex.anyExchange().permitAll());
            return http.build();
        }

        http.authorizeExchange(ex -> ex
                        .pathMatchers(
                                "/api/v1/auth/**",
                                "/.well-known/jwks.json",
                                "/public/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus"
                        ).permitAll()
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
                );
        return http.build();
    }

    static Mono<ServerWebExchangeMatcher.MatchResult> requiresCookieConditionedCsrfProtection(
            ServerWebExchange exchange
    ) {
        var request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();
        if (SAFE_METHODS.contains(request.getMethod())
                || request.getCookies().isEmpty()
                || isIdentityAuthPath(path)
                || hasBearerAuthorization(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
            return ServerWebExchangeMatcher.MatchResult.notMatch();
        }
        return ServerWebExchangeMatcher.MatchResult.match();
    }

    private static boolean hasBearerAuthorization(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.regionMatches(
                true,
                0,
                BEARER_AUTHORIZATION_PREFIX,
                0,
                BEARER_AUTHORIZATION_PREFIX.length()
        )) {
            return false;
        }
        return StringUtils.hasText(authorization.substring(BEARER_AUTHORIZATION_PREFIX.length()));
    }

    private static boolean isIdentityAuthPath(String path) {
        return IDENTITY_AUTH_PATH.equals(path) || path.startsWith(IDENTITY_AUTH_PATH + "/");
    }

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(AgricoreJwtValidators.withIssuerAndAudience(issuer, audience));
        return decoder;
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(new JwtRolesConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }
}

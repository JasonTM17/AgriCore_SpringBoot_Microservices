package com.agricore.gateway.infrastructure.security;

import com.agricore.security.AgricoreJwtValidators;
import com.agricore.security.JwtRolesConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
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
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Gateway-level JWT validation against Identity Service JWKS (issuer + audience).
 * Public auth, JWKS, actuator, and public traceability remain open.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Value("${agricore.security.jwk-set-uri:http://localhost:8081/.well-known/jwks.json}")
    private String jwkSetUri;

    @Value("${agricore.security.jwt-enabled:true}")
    private boolean jwtEnabled;

    @Value("${agricore.security.issuer:https://agricore.local/identity}")
    private String issuer;

    @Value("${agricore.security.audience:agricore-api}")
    private String audience;

    @Bean
    @SuppressWarnings("codeql[java/spring-disabled-csrf-protection]")
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        // The gateway authenticates API calls with explicit bearer tokens and never creates a
        // browser session. Its only cookie-backed route is proxied to Identity, which enforces
        // the exact browser-origin policy and a CSRF matcher before rotating a refresh cookie.
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
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

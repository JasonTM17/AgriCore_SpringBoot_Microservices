package com.agricore.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

/**
 * Shared resource-server security for AgriCore domain services.
 * - Production: validates RS256 JWT via identity JWKS (signature + issuer).
 * - Dev/test: optional X-Dev-User / X-Dev-Roles when agricore.security.dev-mode=true.
 * Never accepts unsigned JWT payloads.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
@EnableConfigurationProperties(AgricoreSecurityProperties.class)
public class DomainServiceSecurityConfig {

    private static final String BEARER_AUTHORIZATION_PREFIX = "Bearer ";
    private static final String DEV_USER_HEADER = "X-Dev-User";
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final CsrfTokenRepository REJECTING_CSRF_TOKEN_REPOSITORY =
            new StatelessRejectingCsrfTokenRepository();

    static final RequestMatcher COOKIE_BACKED_CSRF_MATCHER = request ->
            CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
                    && request.getCookies() != null
                    && request.getCookies().length > 0
                    && !hasBearerAuthorization(request.getHeader(HttpHeaders.AUTHORIZATION))
                    && !StringUtils.hasText(request.getHeader(DEV_USER_HEADER))
                    && !StringUtils.hasText(request.getHeader(INTERNAL_SERVICE_TOKEN_HEADER));

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(AgricoreSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        decoder.setJwtValidator(AgricoreJwtValidators.withIssuerAndAudience(
                properties.getIssuer(),
                properties.getAudience()
        ));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain domainServiceSecurityFilterChain(
            HttpSecurity http,
            AgricoreSecurityProperties properties,
            JwtDecoder jwtDecoder
    ) throws Exception {
        DevHeadersAuthenticationFilter devFilter = new DevHeadersAuthenticationFilter(properties.isDevMode());

        // Explicit bearer, development, and internal-token credentials remain stateless. Unsafe
        // ambient-cookie requests without one of those credentials fail closed; domain services
        // do not mint or persist browser CSRF state because Identity owns cookie authentication.
        http.csrf(csrf -> csrf
                        .csrfTokenRepository(REJECTING_CSRF_TOKEN_REPOSITORY)
                        .requireCsrfProtectionMatcher(COOKIE_BACKED_CSRF_MATCHER)
                )
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // The originating request is authenticated before async MVC/SSE processing starts.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/public/**"
                        ).permitAll()
                        // Inventory validates this narrow service-to-service boundary
                        // with a constant-time shared-token filter and controller check.
                        .requestMatchers("/internal/api/v1/inventory/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                )
                .addFilterBefore(devFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static boolean hasBearerAuthorization(String authorization) {
        if (!StringUtils.hasText(authorization)
                || !authorization.regionMatches(
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

    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRolesConverter());
        return converter;
    }
}

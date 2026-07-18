package com.agricore.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the shipped audience validator used by domain JwtDecoder beans.
 */
class AgricoreJwtValidatorsTest {

    private static final String ISSUER = "https://agricore.test/identity";
    private static final String AUDIENCE = "agricore-api";

    private final OAuth2TokenValidator<Jwt> validator =
            AgricoreJwtValidators.withIssuerAndAudience(ISSUER, AUDIENCE);

    @Test
    void acceptsTokenWithMatchingAudienceString() {
        Jwt jwt = jwt(Map.of(
                "iss", ISSUER,
                "aud", AUDIENCE,
                "sub", "user-1"
        ));
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void acceptsTokenWithMatchingAudienceInCollection() {
        Jwt jwt = jwt(Map.of(
                "iss", ISSUER,
                "aud", List.of("other-api", AUDIENCE),
                "sub", "user-1"
        ));
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWithWrongAudience() {
        Jwt jwt = jwt(Map.of(
                "iss", ISSUER,
                "aud", "wrong-api",
                "sub", "user-1"
        ));
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> "invalid_aud".equals(e.getErrorCode()));
    }

    @Test
    void rejectsTokenWithMissingAudience() {
        Jwt jwt = jwt(Map.of(
                "iss", ISSUER,
                "sub", "user-1"
        ));
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> "missing_aud".equals(e.getErrorCode()));
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .headers(h -> h.put("alg", "RS256"))
                .claims(c -> c.putAll(claims))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}

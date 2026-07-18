package com.agricore.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

/**
 * Composes issuer + audience validators for AgriCore resource servers.
 * Audience must match {@code agricore.security.audience} (typically {@code agricore-api}).
 */
public final class AgricoreJwtValidators {

    private AgricoreJwtValidators() {
    }

    public static OAuth2TokenValidator<Jwt> withIssuerAndAudience(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        if (!StringUtils.hasText(audience)) {
            return issuerValidator;
        }
        return new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                new AudienceValidator(audience.trim())
        );
    }

    /**
     * Accepts {@code aud} as a single string or a collection containing the expected audience.
     */
    static final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

        private final String expectedAudience;

        AudienceValidator(String expectedAudience) {
            this.expectedAudience = expectedAudience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            Object aud = token.getClaims().get("aud");
            if (aud == null) {
                return OAuth2TokenValidatorResult.failure(error("missing_aud", "JWT aud claim is required"));
            }
            if (aud instanceof String s) {
                if (expectedAudience.equals(s)) {
                    return OAuth2TokenValidatorResult.success();
                }
                return OAuth2TokenValidatorResult.failure(error("invalid_aud", "JWT aud does not match expected audience"));
            }
            if (aud instanceof Collection<?> collection) {
                boolean match = collection.stream().map(Object::toString).anyMatch(expectedAudience::equals);
                if (match) {
                    return OAuth2TokenValidatorResult.success();
                }
                return OAuth2TokenValidatorResult.failure(error("invalid_aud", "JWT aud does not contain expected audience"));
            }
            return OAuth2TokenValidatorResult.failure(error("invalid_aud", "JWT aud claim has unsupported type"));
        }

        private static OAuth2Error error(String code, String description) {
            return new OAuth2Error(code, description, null);
        }
    }
}

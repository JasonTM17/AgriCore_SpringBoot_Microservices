package com.agricore.identity.infrastructure.security;

import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    @Test
    void parse_rejectsTokenIssuedForAnotherAudience() throws Exception {
        SecurityProperties properties = securityProperties();
        RsaKeyProvider keyProvider = new RsaKeyProvider(properties);
        keyProvider.init();
        JwtTokenService tokenService = new JwtTokenService(keyProvider, properties);
        Instant now = Instant.now();

        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject("00000000-0000-0000-0000-000000000001")
                .audience().add("another-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(keyProvider.privateKey(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> tokenService.parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parse_rejectsTokenWithoutAudience() throws Exception {
        SecurityProperties properties = securityProperties();
        RsaKeyProvider keyProvider = new RsaKeyProvider(properties);
        keyProvider.init();
        JwtTokenService tokenService = new JwtTokenService(keyProvider, properties);
        Instant now = Instant.now();

        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject("00000000-0000-0000-0000-000000000001")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(keyProvider.privateKey(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> tokenService.parse(token))
                .isInstanceOf(JwtException.class);
    }

    private static SecurityProperties securityProperties() {
        return new SecurityProperties(
                "https://agricore.test/identity",
                "agricore-api",
                300L,
                3600L,
                4,
                5,
                15,
                20,
                "",
                "",
                true,
                false,
                false,
                "agricore_refresh",
                "/api/v1/auth/web",
                false,
                "Strict",
                "http://localhost:5173"
        );
    }
}

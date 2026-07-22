package com.agricore.identity.infrastructure.security;

import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import com.agricore.identity.infrastructure.persistence.PermissionJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {

    @Test
    void parse_rejectsTokenIssuedForAnotherAudience() throws Exception {
        SecurityProperties properties = securityProperties();
        RsaKeyProvider keyProvider = new RsaKeyProvider(properties);
        keyProvider.init();
        JwtTokenService tokenService = new JwtTokenService(
                keyProvider,
                properties,
                mock(PermissionJpaRepository.class)
        );
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
        JwtTokenService tokenService = new JwtTokenService(
                keyProvider,
                properties,
                mock(PermissionJpaRepository.class)
        );
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

    @Test
    void createAccessToken_includesSortedRolePermissionSnapshot() throws Exception {
        SecurityProperties properties = securityProperties();
        RsaKeyProvider keyProvider = new RsaKeyProvider(properties);
        keyProvider.init();
        PermissionJpaRepository permissionRepository = mock(PermissionJpaRepository.class);
        JwtTokenService tokenService = new JwtTokenService(keyProvider, properties, permissionRepository);
        UserEntity user = new UserEntity();
        user.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        user.setEmail("worker@agricore.test");
        user.setFullName("Field Worker");
        List<String> roles = List.of("FIELD_WORKER");
        when(permissionRepository.findGrantedCodesByRoleCodes(roles))
                .thenReturn(List.of("INVENTORY_VIEW", "WORK_EXECUTE"));

        Claims claims = tokenService.parse(tokenService.createAccessToken(user, roles));

        assertThat(claims.get("roles")).isEqualTo(List.of("FIELD_WORKER"));
        assertThat(claims.get("permissions"))
                .isEqualTo(List.of("INVENTORY_VIEW", "WORK_EXECUTE"));
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

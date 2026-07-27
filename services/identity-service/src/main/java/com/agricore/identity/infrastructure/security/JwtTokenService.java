package com.agricore.identity.infrastructure.security;

import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import com.agricore.identity.infrastructure.persistence.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final RsaKeyProvider keyProvider;
    private final SecurityProperties properties;

    public JwtTokenService(
            RsaKeyProvider keyProvider,
            SecurityProperties properties
    ) {
        this.keyProvider = keyProvider;
        this.properties = properties;
    }

    public String createAccessToken(UserEntity user, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.accessTokenTtlSeconds());
        List<String> permissionSnapshot = permissions.stream()
                .distinct()
                .sorted()
                .toList();

        return Jwts.builder()
                .header().keyId(keyProvider.keyId()).and()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .audience().add(properties.audience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("roles", roles)
                .claim("permissions", permissionSnapshot)
                .signWith(keyProvider.privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(keyProvider.publicKey())
                .requireIssuer(properties.issuer())
                .requireAudience(properties.audience())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtlSeconds();
    }
}

package com.agricore.identity.infrastructure.security;

import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import com.agricore.identity.infrastructure.persistence.PermissionJpaRepository;
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
    private final PermissionJpaRepository permissionRepository;

    public JwtTokenService(
            RsaKeyProvider keyProvider,
            SecurityProperties properties,
            PermissionJpaRepository permissionRepository
    ) {
        this.keyProvider = keyProvider;
        this.properties = properties;
        this.permissionRepository = permissionRepository;
    }

    public String createAccessToken(UserEntity user, List<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.accessTokenTtlSeconds());
        List<String> permissions = roles.isEmpty()
                ? List.of()
                : permissionRepository.findGrantedCodesByRoleCodes(roles);

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
                .claim("permissions", permissions)
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

package com.agricore.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.stream.Stream;

public class JwtRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return Stream.concat(
                        extractClaimAuthorities(jwt, "roles", "ROLE_"),
                        extractClaimAuthorities(jwt, "permissions", "PERMISSION_")
                )
                .distinct()
                .toList();
    }

    private static Stream<GrantedAuthority> extractClaimAuthorities(
            Jwt jwt,
            String claimName,
            String authorityPrefix
    ) {
        Object value = jwt.getClaims().get(claimName);
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(claim -> !claim.isBlank())
                    .map(claim -> new SimpleGrantedAuthority(authorityPrefix + claim));
        }
        return Stream.empty();
    }
}

package com.agricore.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRolesConverterTest {

    private final JwtRolesConverter converter = new JwtRolesConverter();

    @Test
    void convertBuildsDistinctRoleAndPermissionAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("00000000-0000-0000-0000-000000000001")
                .claim("roles", List.of("FIELD_WORKER", "FIELD_WORKER"))
                .claim("permissions", List.of("WORK_EXECUTE", "INVENTORY_VIEW", "WORK_EXECUTE"))
                .build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_FIELD_WORKER", "PERMISSION_WORK_EXECUTE", "PERMISSION_INVENTORY_VIEW");
    }

    @Test
    void convertIgnoresMalformedAndBlankClaimEntries() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("roles", List.of("", 42, Map.of("role", "SYSTEM_ADMIN")))
                .claim("permissions", "WORK_EXECUTE")
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }
}

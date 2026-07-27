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
    void convertGrantsNothingWhenClaimsAreAbsent() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-1")
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void convertGrantsNothingForEmptyClaimLists() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("roles", List.of())
                .claim("permissions", List.of())
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void convertGrantsNothingWhenClaimsAreStringsInsteadOfLists() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("roles", "SYSTEM_ADMIN,SALES_STAFF")
                .claim("permissions", "WORK_EXECUTE")
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void convertKeepsValidEntriesAndIgnoresMalformedOrBlankEntries() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("roles", List.of("", 42, Map.of("role", "SYSTEM_ADMIN"), "ADMIN"))
                .claim("permissions", List.of("WORK_EXECUTE", Map.of("permission", "INVENTORY_VIEW")))
                .build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "PERMISSION_WORK_EXECUTE");
    }
}

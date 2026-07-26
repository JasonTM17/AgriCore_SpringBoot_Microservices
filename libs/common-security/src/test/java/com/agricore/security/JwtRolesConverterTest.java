package com.agricore.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Turns the {@code roles} claim of a verified token into authorities. Every {@code @PreAuthorize}
 * on the platform is decided by what this returns.
 */
class JwtRolesConverterTest {

    private final JwtRolesConverter converter = new JwtRolesConverter();

    @Test
    void prefixesEachRoleForSpringSecurity() {
        assertThat(converter.convert(jwtWithRoles(List.of("SYSTEM_ADMIN", "SALES_STAFF"))))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SYSTEM_ADMIN", "ROLE_SALES_STAFF");
    }

    @Test
    void grantsNothingWhenTheClaimIsAbsent() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void grantsNothingForAnEmptyRoleList() {
        assertThat(converter.convert(jwtWithRoles(List.of()))).isEmpty();
    }

    /**
     * Pins the behaviour for a token whose {@code roles} claim is a comma-separated string rather
     * than an array — a plausible mistake in a hand-rolled issuer.
     *
     * <p>The result is zero authorities, so such a token authenticates and then fails every
     * authorization check. It does not silently gain a role named {@code ROLE_ADMIN,USER}, which is
     * the outcome that would matter. Fail-closed is the correct behaviour here; this test exists so
     * that a later "convenience" change to accept the string form has to be a deliberate one.
     */
    @Test
    void grantsNothingWhenTheClaimIsAStringInsteadOfAList() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .claim("roles", "SYSTEM_ADMIN,SALES_STAFF")
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    /**
     * A JSON array carries whatever the issuer put in it. Non-string entries are stringified rather
     * than dropped, so the count still matches what the token claimed.
     */
    @Test
    void stringifiesNonStringEntries() {
        assertThat(converter.convert(jwtWithRoles(List.of("ADMIN", 42))))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_42");
    }

    private static Jwt jwtWithRoles(List<?> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .claim("roles", roles)
                .build();
    }
}

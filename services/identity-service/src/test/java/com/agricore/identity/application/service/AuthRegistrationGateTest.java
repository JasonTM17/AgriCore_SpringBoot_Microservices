package com.agricore.identity.application.service;

import com.agricore.identity.api.request.RegisterRequest;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import com.agricore.identity.infrastructure.persistence.RefreshTokenJpaRepository;
import com.agricore.identity.infrastructure.persistence.RoleJpaRepository;
import com.agricore.identity.infrastructure.persistence.UserJpaRepository;
import com.agricore.identity.infrastructure.security.JwtTokenService;
import com.agricore.identity.infrastructure.security.LoginRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives real {@link AuthApplicationService#register} registration gate.
 * When registration is disabled, no user row is written.
 */
class AuthRegistrationGateTest {

    private static SecurityProperties props(boolean registrationEnabled) {
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
                registrationEnabled,
                true,
                false
        );
    }

    private AuthApplicationService service(UserJpaRepository users, SecurityProperties security) {
        return new AuthApplicationService(
                users,
                mock(RoleJpaRepository.class),
                mock(RefreshTokenJpaRepository.class),
                mock(PasswordEncoder.class),
                mock(JwtTokenService.class),
                security,
                mock(LoginRateLimiter.class)
        );
    }

    @Test
    void register_throwsRegistrationDisabled_whenFlagFalse() {
        UserJpaRepository users = mock(UserJpaRepository.class);
        AuthApplicationService auth = service(users, props(false));

        RegisterRequest request = new RegisterRequest(
                "blocked@agricore.test",
                "Secret123!",
                "Blocked User"
        );

        assertThatThrownBy(() -> auth.register(request))
                .isInstanceOf(IdentityException.class)
                .satisfies(ex -> {
                    IdentityException ie = (IdentityException) ex;
                    assertThat(ie.getCode()).isEqualTo("REGISTRATION_DISABLED");
                    assertThat(ie.getHttpStatus()).isEqualTo(403);
                });

        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
        verify(users, never()).existsByEmailIgnoreCase(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void register_checksEmailWhenEnabled() {
        UserJpaRepository users = mock(UserJpaRepository.class);
        when(users.existsByEmailIgnoreCase("exists@agricore.test")).thenReturn(true);
        AuthApplicationService auth = service(users, props(true));

        RegisterRequest request = new RegisterRequest(
                "exists@agricore.test",
                "Secret123!",
                "Existing User"
        );

        assertThatThrownBy(() -> auth.register(request))
                .isInstanceOf(IdentityException.class)
                .satisfies(ex -> {
                    IdentityException ie = (IdentityException) ex;
                    assertThat(ie.getCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
                    assertThat(ie.getHttpStatus()).isEqualTo(409);
                });
    }
}

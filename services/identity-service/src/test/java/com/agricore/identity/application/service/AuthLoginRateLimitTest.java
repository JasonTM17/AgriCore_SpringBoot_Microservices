package com.agricore.identity.application.service;

import com.agricore.identity.api.request.LoginRequest;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives real {@link AuthApplicationService#login} when the rate limiter denies.
 * Proves RATE_LIMITED is raised before user lookup.
 */
class AuthLoginRateLimitTest {

    @Test
    void login_throwsRateLimited_whenLimiterDenies() {
        UserJpaRepository users = mock(UserJpaRepository.class);
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        when(limiter.allow(anyString())).thenReturn(false);

        SecurityProperties security = new SecurityProperties(
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
                false
        );

        AuthApplicationService auth = new AuthApplicationService(
                users,
                mock(RoleJpaRepository.class),
                mock(RefreshTokenJpaRepository.class),
                mock(PasswordEncoder.class),
                mock(JwtTokenService.class),
                security,
                limiter,
                mock(IdentityOutboxWriter.class),
                mock(AuthFailureRecorder.class)
        );

        LoginRequest request = new LoginRequest("any@agricore.test", "Secret123!");

        assertThatThrownBy(() -> auth.login(request, "203.0.113.10", "junit"))
                .isInstanceOf(IdentityException.class)
                .satisfies(ex -> {
                    IdentityException ie = (IdentityException) ex;
                    assertThat(ie.getCode()).isEqualTo("RATE_LIMITED");
                    assertThat(ie.getHttpStatus()).isEqualTo(429);
                });

        verify(users, never()).findByEmailIgnoreCase(anyString());
        verify(limiter).allow("203.0.113.10");
    }
}

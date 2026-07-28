package com.agricore.identity.application.service;

import com.agricore.identity.api.request.LoginRequest;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.domain.model.UserStatus;
import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import com.agricore.identity.infrastructure.persistence.RefreshTokenJpaRepository;
import com.agricore.identity.infrastructure.persistence.RoleJpaRepository;
import com.agricore.identity.infrastructure.persistence.UserJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.RefreshTokenEntity;
import com.agricore.identity.infrastructure.persistence.entity.UserEntity;
import com.agricore.identity.infrastructure.security.JwtTokenService;
import com.agricore.identity.infrastructure.security.LoginRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthApplicationServiceRejectionTest {

    private final UserJpaRepository userRepository = mock(UserJpaRepository.class);
    private final RoleJpaRepository roleRepository = mock(RoleJpaRepository.class);
    private final RefreshTokenJpaRepository refreshTokenRepository = mock(RefreshTokenJpaRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final EffectivePermissionService effectivePermissionService = mock(EffectivePermissionService.class);
    private final LoginRateLimiter loginRateLimiter = mock(LoginRateLimiter.class);
    private final IdentityOutboxWriter outboxWriter = mock(IdentityOutboxWriter.class);

    @Test
    void login_rejectsDisabledAccountBeforeCheckingThePassword() {
        UserEntity disabledUser = user(UserStatus.DISABLED);
        when(loginRateLimiter.allow("127.0.0.1")).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("disabled@example.test")).thenReturn(Optional.of(disabledUser));

        IdentityException exception = assertThrows(
                IdentityException.class,
                () -> service().login(new LoginRequest("disabled@example.test", "ignored"), "127.0.0.1", null)
        );

        assertThat(exception.getCode()).isEqualTo("ACCOUNT_DISABLED");
        assertThat(exception.getHttpStatus()).isEqualTo(403);
        verifyNoInteractions(passwordEncoder, refreshTokenRepository, jwtTokenService, effectivePermissionService, outboxWriter);
    }

    @Test
    void refresh_rejectsAnInactiveAccountAfterResolvingItsTokenOwner() {
        UUID userId = UUID.randomUUID();
        UserEntity lockedUser = user(UserStatus.LOCKED);
        RefreshTokenEntity activeToken = refreshTokenExpiringAt(Instant.now().plusSeconds(60));
        when(refreshTokenRepository.findUserIdByTokenHash(anyString())).thenReturn(Optional.of(userId));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(lockedUser));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(activeToken));

        IdentityException exception = assertThrows(
                IdentityException.class,
                () -> service().refresh("synthetic-refresh-input", "127.0.0.1", null)
        );

        assertThat(exception.getCode()).isEqualTo("ACCOUNT_INACTIVE");
        assertThat(exception.getHttpStatus()).isEqualTo(403);
        verifyNoInteractions(jwtTokenService, effectivePermissionService, outboxWriter);
    }

    @Test
    void refresh_revokesAndRejectsAnExpiredToken() {
        UUID userId = UUID.randomUUID();
        UserEntity activeUser = user(UserStatus.ACTIVE);
        RefreshTokenEntity expiredToken = refreshTokenExpiringAt(Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findUserIdByTokenHash(anyString())).thenReturn(Optional.of(userId));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        IdentityException exception = assertThrows(
                IdentityException.class,
                () -> service().refresh("synthetic-refresh-input", "127.0.0.1", null)
        );

        assertThat(exception.getCode()).isEqualTo("REFRESH_TOKEN_EXPIRED");
        assertThat(exception.getHttpStatus()).isEqualTo(401);
        verify(refreshTokenRepository).save(expiredToken);
        assertThat(expiredToken.getRevokedAt()).isNotNull();
        verifyNoInteractions(jwtTokenService, effectivePermissionService, outboxWriter);
    }

    private AuthApplicationService service() {
        return new AuthApplicationService(
                userRepository,
                roleRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtTokenService,
                effectivePermissionService,
                securityProperties(),
                loginRateLimiter,
                outboxWriter
        );
    }

    private static UserEntity user(UserStatus status) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setStatus(status);
        return user;
    }

    private static RefreshTokenEntity refreshTokenExpiringAt(Instant expiresAt) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setExpiresAt(expiresAt);
        return token;
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
                "agricore_refresh",
                "/api/v1/auth/web",
                false,
                "Strict",
                "http://localhost:5173"
        );
    }
}

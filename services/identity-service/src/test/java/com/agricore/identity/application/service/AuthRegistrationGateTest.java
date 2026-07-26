package com.agricore.identity.application.service;

import com.agricore.identity.api.request.RegisterRequest;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import com.agricore.identity.infrastructure.persistence.RefreshTokenJpaRepository;
import com.agricore.identity.infrastructure.persistence.RoleJpaRepository;
import com.agricore.identity.infrastructure.persistence.UserJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.RoleEntity;
import com.agricore.identity.infrastructure.security.JwtTokenService;
import com.agricore.identity.infrastructure.security.LoginRateLimiter;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
                false,
                "agricore_refresh",
                "/api/v1/auth/web",
                false,
                "Strict",
                "http://localhost:5173"
        );
    }

    private AuthApplicationService service(UserJpaRepository users, SecurityProperties security) {
        return service(users, mock(RoleJpaRepository.class), security);
    }

    private AuthApplicationService service(
            UserJpaRepository users,
            RoleJpaRepository roles,
            SecurityProperties security
    ) {
        return new AuthApplicationService(
                users,
                roles,
                mock(RefreshTokenJpaRepository.class),
                mock(PasswordEncoder.class),
                mock(JwtTokenService.class),
                mock(EffectivePermissionService.class),
                security,
                mock(LoginRateLimiter.class),
                mock(IdentityOutboxWriter.class)
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

        verify(users, never()).saveAndFlush(any());
        verify(users, never()).existsByEmailIgnoreCase(anyString());
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

    @Test
    void register_translatesUniqueConstraintRaceToConflict() {
        UserJpaRepository users = mock(UserJpaRepository.class);
        RoleJpaRepository roles = mock(RoleJpaRepository.class);
        RoleEntity fieldWorker = new RoleEntity();
        fieldWorker.setCode("FIELD_WORKER");
        when(users.existsByEmailIgnoreCase("race@agricore.test")).thenReturn(false);
        when(roles.findByCode("FIELD_WORKER")).thenReturn(Optional.of(fieldWorker));
        ConstraintViolationException uniqueEmailViolation = new ConstraintViolationException(
                "duplicate email",
                new SQLException("duplicate email", "23505"),
                "insert into users",
                "uk_users_email"
        );
        when(users.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("duplicate email", uniqueEmailViolation)
        );
        AuthApplicationService auth = service(users, roles, props(true));

        RegisterRequest request = new RegisterRequest(
                "race@agricore.test",
                "Secret123!",
                "Concurrent User"
        );

        assertThatThrownBy(() -> auth.register(request))
                .isInstanceOf(IdentityException.class)
                .satisfies(ex -> {
                    IdentityException exception = (IdentityException) ex;
                    assertThat(exception.getCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
                    assertThat(exception.getHttpStatus()).isEqualTo(409);
                });

        verify(users).saveAndFlush(any());
    }

    @Test
    void register_doesNotMisreportUnrelatedIntegrityViolationsAsDuplicateEmail() {
        UserJpaRepository users = mock(UserJpaRepository.class);
        RoleJpaRepository roles = mock(RoleJpaRepository.class);
        RoleEntity fieldWorker = new RoleEntity();
        fieldWorker.setCode("FIELD_WORKER");
        when(users.existsByEmailIgnoreCase("valid@agricore.test")).thenReturn(false);
        when(roles.findByCode("FIELD_WORKER")).thenReturn(Optional.of(fieldWorker));
        DataIntegrityViolationException persistenceFailure =
                new DataIntegrityViolationException("foreign-key violation");
        when(users.saveAndFlush(any())).thenThrow(persistenceFailure);
        AuthApplicationService auth = service(users, roles, props(true));

        RegisterRequest request = new RegisterRequest(
                "valid@agricore.test",
                "Secret123!",
                "Valid User"
        );

        assertThatThrownBy(() -> auth.register(request)).isSameAs(persistenceFailure);
    }
}

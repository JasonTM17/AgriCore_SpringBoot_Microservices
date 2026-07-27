package com.agricore.identity.application.service;

import com.agricore.identity.TestRedisConfig;
import com.agricore.identity.api.request.LoginRequest;
import com.agricore.identity.api.request.RegisterRequest;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.domain.model.UserStatus;
import com.agricore.identity.infrastructure.persistence.UserJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lockout state has to outlive the transaction that rejects the login.
 *
 * <p>Rejection throws {@code IdentityException}, an unchecked exception, which marks the
 * surrounding transaction rollback-only. A counter written on that path and left to the caller's
 * transaction is discarded, and the account never locks no matter how many guesses arrive. These
 * tests read the row back after the throw, so they fail if the write is ever folded back into the
 * rejecting transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AuthLockoutPersistenceTest {

    private static final String PASSWORD = "Secret123!";
    private static final String WRONG_PASSWORD = "WrongSecret123!";

    @Autowired
    private AuthApplicationService authService;

    @Autowired
    private UserJpaRepository userRepository;

    private UUID registerUser(String email) {
        return authService.register(new RegisterRequest(email, PASSWORD, "Lockout User")).id();
    }

    private void attemptBadLogin(String email) {
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, WRONG_PASSWORD), "203.0.113.7", "junit"))
                .isInstanceOf(IdentityException.class)
                .satisfies(ex -> assertThat(((IdentityException) ex).getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void failedLogin_persistsCounter_despiteRejectionRollingBackTheCallerTransaction() {
        String email = "lockout" + System.nanoTime() + "@agricore.test";
        UUID userId = registerUser(email);

        attemptBadLogin(email);

        UserEntity user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void repeatedFailures_lockAccountAtThreshold_andSubsequentLoginIsRefused() {
        String email = "lockthreshold" + System.nanoTime() + "@agricore.test";
        UUID userId = registerUser(email);

        // application-test.yml sets max-failed-logins: 5
        for (int i = 0; i < 5; i++) {
            attemptBadLogin(email);
        }

        UserEntity locked = userRepository.findById(userId).orElseThrow();
        assertThat(locked.getFailedLoginCount()).isEqualTo(5);
        assertThat(locked.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(locked.getLockedUntil()).isNotNull();

        // The lock has to actually gate the endpoint, not merely exist in a column.
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, PASSWORD), "203.0.113.7", "junit"))
                .isInstanceOf(IdentityException.class)
                .satisfies(ex -> {
                    IdentityException ie = (IdentityException) ex;
                    assertThat(ie.getCode()).isEqualTo("ACCOUNT_LOCKED");
                    assertThat(ie.getHttpStatus()).isEqualTo(423);
                });
    }

    @Test
    void successfulLogin_resetsCounter() {
        String email = "lockreset" + System.nanoTime() + "@agricore.test";
        UUID userId = registerUser(email);

        attemptBadLogin(email);
        assertThat(userRepository.findById(userId).orElseThrow().getFailedLoginCount()).isEqualTo(1);

        authService.login(new LoginRequest(email, PASSWORD), "203.0.113.7", "junit");

        assertThat(userRepository.findById(userId).orElseThrow().getFailedLoginCount()).isZero();
    }
}

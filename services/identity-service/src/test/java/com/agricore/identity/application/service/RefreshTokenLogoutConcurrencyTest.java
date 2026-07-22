package com.agricore.identity.application.service;

import com.agricore.identity.TestRedisConfig;
import com.agricore.identity.api.request.LoginRequest;
import com.agricore.identity.api.request.RegisterRequest;
import com.agricore.identity.api.response.AuthTokensResponse;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.infrastructure.persistence.RefreshTokenJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.RefreshTokenEntity;
import com.agricore.identity.infrastructure.security.TokenHashing;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class RefreshTokenLogoutConcurrencyTest {

    @Autowired
    private AuthApplicationService authService;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenRepository;

    @Test
    void logoutWithRotatedTokenRevokesItsActiveReplacement() {
        AuthTokensResponse login = registerAndLogin("logout-after-rotation");
        AuthTokensResponse rotated = authService.refresh(
                login.refreshToken(),
                "127.0.0.1",
                "logout-race-test"
        );

        authService.logout(login.refreshToken());

        UUID familyId = familyIdOf(login.refreshToken());
        assertThat(refreshTokenRepository.countByFamilyIdAndRevokedAtIsNull(familyId)).isZero();
        assertThatThrownBy(() -> authService.refresh(
                rotated.refreshToken(),
                "127.0.0.1",
                "logout-race-test"
        )).isInstanceOfSatisfying(
                IdentityException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("REFRESH_TOKEN_REUSE")
        );
    }

    @Test
    void logoutOfOldTokenAndRefreshOfReplacementLeaveNoUsableFamilyToken() throws Exception {
        AuthTokensResponse login = registerAndLogin("concurrent-logout-refresh");
        AuthTokensResponse active = authService.refresh(
                login.refreshToken(),
                "127.0.0.1",
                "logout-race-test"
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> refresh = executor.submit(() -> attemptRefresh(active.refreshToken(), ready, start));
            Future<Void> logout = executor.submit(() -> {
                awaitStart(ready, start);
                authService.logout(login.refreshToken());
                return null;
            });

            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();

            String replacement = refresh.get(10, SECONDS);
            logout.get(10, SECONDS);

            UUID familyId = familyIdOf(login.refreshToken());
            assertThat(refreshTokenRepository.countByFamilyIdAndRevokedAtIsNull(familyId)).isZero();
            if (replacement != null) {
                assertThatThrownBy(() -> authService.refresh(
                        replacement,
                        "127.0.0.1",
                        "logout-race-test"
                )).isInstanceOf(IdentityException.class);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private AuthTokensResponse registerAndLogin(String prefix) {
        String email = prefix + '-' + System.nanoTime() + "@agricore.test";
        authService.register(new RegisterRequest(email, "Secret123!", "Logout Race User"));
        return authService.login(
                new LoginRequest(email, "Secret123!"),
                "127.0.0.1",
                "logout-race-test"
        );
    }

    private UUID familyIdOf(String refreshToken) {
        RefreshTokenEntity token = refreshTokenRepository
                .findByTokenHash(TokenHashing.sha256Hex(refreshToken))
                .orElseThrow();
        return token.getFamilyId();
    }

    private String attemptRefresh(
            String refreshToken,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        awaitStart(ready, start);
        try {
            return authService.refresh(refreshToken, "127.0.0.1", "logout-race-test").refreshToken();
        } catch (IdentityException exception) {
            assertThat(exception.getCode()).isIn("INVALID_REFRESH_TOKEN", "REFRESH_TOKEN_REUSE");
            return null;
        }
    }

    private static void awaitStart(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, SECONDS)) {
            throw new IllegalStateException("Concurrent auth test did not start in time");
        }
    }
}

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

import java.util.List;
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
class RefreshTokenRotationConcurrencyTest {

    @Autowired
    private AuthApplicationService authService;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenRepository;

    @Test
    void concurrentRefresh_reuseRevokesTheWholeFamily() throws Exception {
        String email = "refresh-race-" + System.nanoTime() + "@agricore.test";
        authService.register(new RegisterRequest(email, "Secret123!", "Refresh Race User"));
        AuthTokensResponse login = authService.login(
                new LoginRequest(email, "Secret123!"),
                "127.0.0.1",
                "concurrency-test"
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<RefreshAttempt> first = executor.submit(
                    () -> attemptRefresh(login.refreshToken(), ready, start)
            );
            Future<RefreshAttempt> second = executor.submit(
                    () -> attemptRefresh(login.refreshToken(), ready, start)
            );

            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();

            List<RefreshAttempt> attempts = List.of(first.get(10, SECONDS), second.get(10, SECONDS));
            assertThat(attempts).filteredOn(RefreshAttempt::succeeded).hasSize(1);
            assertThat(attempts)
                    .filteredOn(attempt -> !attempt.succeeded())
                    .extracting(RefreshAttempt::errorCode)
                    .containsExactly("REFRESH_TOKEN_REUSE");

            RefreshTokenEntity original = refreshTokenRepository
                    .findByTokenHash(TokenHashing.sha256Hex(login.refreshToken()))
                    .orElseThrow();
            UUID familyId = original.getFamilyId();
            List<RefreshTokenEntity> family = refreshTokenRepository.findAll().stream()
                    .filter(token -> familyId.equals(token.getFamilyId()))
                    .toList();

            assertThat(family).hasSize(2);
            assertThat(family).allMatch(token -> token.getRevokedAt() != null);
            assertThat(refreshTokenRepository.countByFamilyIdAndRevokedAtIsNull(familyId)).isZero();

            String returnedReplacement = attempts.stream()
                    .filter(RefreshAttempt::succeeded)
                    .findFirst()
                    .orElseThrow()
                    .refreshToken();
            assertThatThrownBy(
                    () -> authService.refresh(returnedReplacement, "127.0.0.1", "concurrency-test")
            ).isInstanceOfSatisfying(
                    IdentityException.class,
                    exception -> assertThat(exception.getCode()).isEqualTo("REFRESH_TOKEN_REUSE")
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reusedRotatedTokenImmediately_revokesWholeFamily() {
        String email = "refresh-reuse-" + System.nanoTime() + "@agricore.test";
        authService.register(new RegisterRequest(email, "Secret123!", "Refresh Reuse User"));
        AuthTokensResponse login = authService.login(
                new LoginRequest(email, "Secret123!"),
                "127.0.0.1",
                "reuse-test"
        );
        authService.refresh(login.refreshToken(), "127.0.0.1", "reuse-test");

        RefreshTokenEntity original = refreshTokenRepository
                .findByTokenHash(TokenHashing.sha256Hex(login.refreshToken()))
                .orElseThrow();

        assertThatThrownBy(
                () -> authService.refresh(login.refreshToken(), "127.0.0.1", "reuse-test")
        ).isInstanceOfSatisfying(
                IdentityException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("REFRESH_TOKEN_REUSE")
        );

        assertThat(refreshTokenRepository.countByFamilyIdAndRevokedAtIsNull(original.getFamilyId())).isZero();
    }

    private RefreshAttempt attemptRefresh(
            String refreshToken,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, SECONDS)) {
            return RefreshAttempt.failed("START_TIMEOUT");
        }

        try {
            AuthTokensResponse response = authService.refresh(
                    refreshToken,
                    "127.0.0.1",
                    "concurrency-test"
            );
            return RefreshAttempt.succeeded(response.refreshToken());
        } catch (IdentityException exception) {
            return RefreshAttempt.failed(exception.getCode());
        }
    }

    private record RefreshAttempt(boolean succeeded, String refreshToken, String errorCode) {

        private static RefreshAttempt succeeded(String refreshToken) {
            return new RefreshAttempt(true, refreshToken, null);
        }

        private static RefreshAttempt failed(String errorCode) {
            return new RefreshAttempt(false, null, errorCode);
        }
    }
}

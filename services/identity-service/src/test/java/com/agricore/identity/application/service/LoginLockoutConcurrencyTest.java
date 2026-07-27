package com.agricore.identity.application.service;

import com.agricore.identity.TestRedisConfig;
import com.agricore.identity.api.request.LoginRequest;
import com.agricore.identity.api.request.RegisterRequest;
import com.agricore.identity.domain.exception.IdentityException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class LoginLockoutConcurrencyTest {

    @Autowired
    private AuthApplicationService authService;

    @Test
    void concurrentFailuresAreCountedWithoutLostUpdates() throws Exception {
        String email = "concurrent-lockout" + System.nanoTime() + "@agricore.test";
        authService.register(new RegisterRequest(email, "Secret123!", "Concurrent Lockout User"));

        int attempts = 5;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);

        try {
            List<Future<IdentityException>> results = new ArrayList<>();
            for (int attempt = 0; attempt < attempts; attempt++) {
                results.add(executor.submit(() -> attemptLogin(email, ready, start)));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<IdentityException> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS))
                        .extracting(IdentityException::getCode)
                        .isEqualTo("INVALID_CREDENTIALS");
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThatThrownBy(() -> authService.login(
                new LoginRequest(email, "Secret123!"),
                "203.0.113.10",
                "junit"
        )).isInstanceOf(IdentityException.class)
                .satisfies(error -> {
                    IdentityException exception = (IdentityException) error;
                    assertThat(exception.getCode()).isEqualTo("ACCOUNT_LOCKED");
                    assertThat(exception.getHttpStatus()).isEqualTo(423);
                });
    }

    private IdentityException attemptLogin(String email, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            authService.login(new LoginRequest(email, "WrongPass1!"), "203.0.113.10", "junit");
            throw new AssertionError("Wrong password unexpectedly authenticated");
        } catch (IdentityException exception) {
            return exception;
        }
    }
}

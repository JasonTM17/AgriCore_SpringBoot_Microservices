package com.agricore.identity.infrastructure.persistence.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenEntityTest {

    private static final Instant NOW = Instant.parse("2026-07-28T10:15:30Z");

    @Test
    void isActive_returnsTrueForAnUnrevokedTokenThatExpiresAfterNow() {
        RefreshTokenEntity token = tokenExpiringAt(NOW.plusSeconds(1));

        assertThat(token.isActive(NOW)).isTrue();
    }

    @Test
    void isActive_returnsFalseForARevokedTokenEvenWhenItsExpiryIsInTheFuture() {
        RefreshTokenEntity token = tokenExpiringAt(NOW.plusSeconds(1));
        token.setRevokedAt(NOW.minusSeconds(1));

        assertThat(token.isActive(NOW)).isFalse();
    }

    @Test
    void isActive_returnsFalseForAnExpiredToken() {
        RefreshTokenEntity token = tokenExpiringAt(NOW.minusNanos(1));

        assertThat(token.isActive(NOW)).isFalse();
    }

    @Test
    void isActive_returnsFalseWhenExpiryEqualsNow() {
        RefreshTokenEntity token = tokenExpiringAt(NOW);

        assertThat(token.isActive(NOW)).isFalse();
    }

    private static RefreshTokenEntity tokenExpiringAt(Instant expiresAt) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setExpiresAt(expiresAt);
        return token;
    }
}

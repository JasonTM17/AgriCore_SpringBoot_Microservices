package com.agricore.identity.application.service;

import com.agricore.identity.domain.model.UserStatus;
import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import com.agricore.identity.infrastructure.persistence.RefreshTokenJpaRepository;
import com.agricore.identity.infrastructure.persistence.UserJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists security state that must outlive the transaction which rejects the request.
 *
 * <p>Authentication failures reject by throwing {@code IdentityException}, an unchecked exception,
 * so Spring marks the caller's transaction rollback-only. Any lockout counter or family revocation
 * written on that path would be discarded together with the rejection, leaving the defence looking
 * present in the code and absent at runtime. Each method here therefore commits in its own
 * transaction.
 *
 * <p>This is a separate component on purpose. {@code REQUIRES_NEW} is applied by the Spring proxy,
 * so annotating a method the caller invokes on {@code this} silently does nothing — the same class
 * of invisible failure being fixed.
 */
@Component
public class AuthFailureRecorder {

    private final UserJpaRepository userRepository;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final SecurityProperties securityProperties;

    public AuthFailureRecorder(
            UserJpaRepository userRepository,
            RefreshTokenJpaRepository refreshTokenRepository,
            SecurityProperties securityProperties
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.securityProperties = securityProperties;
    }

    /**
     * Increments the failed-login counter and locks the account once it reaches the configured
     * threshold. Loads the user by id rather than accepting the caller's managed entity, so the
     * rejecting transaction holds no dirty copy of the row this one updates.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(UUID userId, Instant now) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        int failures = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(failures);
        if (failures >= securityProperties.maxFailedLogins()) {
            user.setLockedUntil(now.plusSeconds(securityProperties.lockoutDurationMinutes() * 60L));
            user.setStatus(UserStatus.LOCKED);
        }
        user.setUpdatedAt(now);
        userRepository.save(user);
    }

    /**
     * Revokes every live token in a family after reuse of an already-revoked token.
     * Committing separately is the whole point: the reuse itself is rejected, and a rolled-back
     * revocation would leave a known-stolen token family valid.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeCompromisedFamily(UUID familyId, Instant now) {
        refreshTokenRepository.revokeFamily(familyId, now);
    }
}

package com.agricore.identity.application.service;

import com.agricore.identity.api.request.LoginRequest;
import com.agricore.identity.api.request.RegisterRequest;
import com.agricore.identity.api.response.AuthTokensResponse;
import com.agricore.identity.api.response.UserResponse;
import com.agricore.identity.domain.exception.IdentityException;
import com.agricore.identity.domain.model.RoleCode;
import com.agricore.identity.domain.model.UserStatus;
import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import com.agricore.identity.infrastructure.persistence.RefreshTokenJpaRepository;
import com.agricore.identity.infrastructure.persistence.RoleJpaRepository;
import com.agricore.identity.infrastructure.persistence.UserJpaRepository;
import com.agricore.identity.infrastructure.persistence.entity.RefreshTokenEntity;
import com.agricore.identity.infrastructure.persistence.entity.RoleEntity;
import com.agricore.identity.infrastructure.persistence.entity.UserEntity;
import com.agricore.identity.infrastructure.security.JwtTokenService;
import com.agricore.identity.infrastructure.security.LoginRateLimiter;
import com.agricore.identity.infrastructure.security.TokenHashing;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthApplicationService {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final SecurityProperties securityProperties;
    private final LoginRateLimiter loginRateLimiter;

    public AuthApplicationService(
            UserJpaRepository userRepository,
            RoleJpaRepository roleRepository,
            RefreshTokenJpaRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            SecurityProperties securityProperties,
            LoginRateLimiter loginRateLimiter
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.securityProperties = securityProperties;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IdentityException("EMAIL_ALREADY_EXISTS", "Email is already registered", 409);
        }

        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        RoleEntity defaultRole = roleRepository.findByCode(RoleCode.FIELD_WORKER.name())
                .orElseThrow(() -> new IdentityException("ROLE_MISSING", "Default role not seeded", 500));
        user.setRoles(Set.of(defaultRole));

        userRepository.save(user);
        return toUserResponse(user);
    }

    @Transactional
    public AuthTokensResponse login(LoginRequest request, String clientIp, String userAgent) {
        if (!loginRateLimiter.allow(clientIp == null ? "unknown" : clientIp)) {
            throw new IdentityException("RATE_LIMITED", "Too many login attempts. Try again later.", 429);
        }

        String email = request.email().trim().toLowerCase();
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IdentityException("INVALID_CREDENTIALS", "Invalid email or password", 401));

        Instant now = Instant.now();
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new IdentityException("ACCOUNT_DISABLED", "Account is disabled", 403);
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new IdentityException("ACCOUNT_LOCKED", "Account is temporarily locked", 423);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user, now);
            throw new IdentityException("INVALID_CREDENTIALS", "Invalid email or password", 401);
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        return issueTokens(user, clientIp, userAgent, UUID.randomUUID());
    }

    @Transactional
    public AuthTokensResponse refresh(String refreshToken, String clientIp, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IdentityException("INVALID_REFRESH_TOKEN", "Refresh token is required", 401);
        }

        String hash = TokenHashing.sha256Hex(refreshToken);
        RefreshTokenEntity existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IdentityException("INVALID_REFRESH_TOKEN", "Invalid refresh token", 401));

        Instant now = Instant.now();

        // Reuse of a revoked token in a family → revoke entire family (theft detection)
        if (existing.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(existing.getFamilyId(), now);
            throw new IdentityException("REFRESH_TOKEN_REUSE", "Refresh token reuse detected. Session family revoked.", 401);
        }

        if (existing.getExpiresAt().isBefore(now)) {
            throw new IdentityException("REFRESH_TOKEN_EXPIRED", "Refresh token expired", 401);
        }

        UserEntity user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new IdentityException("USER_NOT_FOUND", "User not found", 401));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IdentityException("ACCOUNT_INACTIVE", "Account is not active", 403);
        }

        String newRaw = TokenHashing.generateOpaqueToken();
        RefreshTokenEntity replacement = new RefreshTokenEntity();
        replacement.setId(UUID.randomUUID());
        replacement.setUserId(user.getId());
        replacement.setTokenHash(TokenHashing.sha256Hex(newRaw));
        replacement.setFamilyId(existing.getFamilyId());
        replacement.setExpiresAt(now.plusSeconds(securityProperties.refreshTokenTtlSeconds()));
        replacement.setCreatedAt(now);
        replacement.setUserAgent(userAgent);
        replacement.setIpAddress(clientIp);
        refreshTokenRepository.save(replacement);

        existing.setRevokedAt(now);
        existing.setReplacedBy(replacement.getId());
        refreshTokenRepository.save(existing);

        List<String> roles = roleNames(user);
        String accessToken = jwtTokenService.createAccessToken(user, roles);

        return new AuthTokensResponse(
                accessToken,
                newRaw,
                "Bearer",
                jwtTokenService.accessTokenTtlSeconds(),
                toUserResponse(user)
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String hash = TokenHashing.sha256Hex(refreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            Instant now = Instant.now();
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IdentityException("USER_NOT_FOUND", "User not found", 404));
        return toUserResponse(user);
    }

    private void handleFailedLogin(UserEntity user, Instant now) {
        int failures = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(failures);
        if (failures >= securityProperties.maxFailedLogins()) {
            user.setLockedUntil(now.plusSeconds(securityProperties.lockoutDurationMinutes() * 60L));
            user.setStatus(UserStatus.LOCKED);
        }
        user.setUpdatedAt(now);
        userRepository.save(user);
    }

    private AuthTokensResponse issueTokens(UserEntity user, String clientIp, String userAgent, UUID familyId) {
        Instant now = Instant.now();
        String rawRefresh = TokenHashing.generateOpaqueToken();

        RefreshTokenEntity refresh = new RefreshTokenEntity();
        refresh.setId(UUID.randomUUID());
        refresh.setUserId(user.getId());
        refresh.setTokenHash(TokenHashing.sha256Hex(rawRefresh));
        refresh.setFamilyId(familyId);
        refresh.setExpiresAt(now.plusSeconds(securityProperties.refreshTokenTtlSeconds()));
        refresh.setCreatedAt(now);
        refresh.setUserAgent(userAgent);
        refresh.setIpAddress(clientIp);
        refreshTokenRepository.save(refresh);

        List<String> roles = roleNames(user);
        String accessToken = jwtTokenService.createAccessToken(user, roles);

        return new AuthTokensResponse(
                accessToken,
                rawRefresh,
                "Bearer",
                jwtTokenService.accessTokenTtlSeconds(),
                toUserResponse(user)
        );
    }

    private static List<String> roleNames(UserEntity user) {
        return user.getRoles().stream().map(RoleEntity::getCode).sorted().collect(Collectors.toList());
    }

    private static UserResponse toUserResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus().name(),
                roleNames(user),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}

package com.agricore.farm.application.service;

import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.infrastructure.persistence.FarmMembershipJpaRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class FarmAuthorizationService {

    private static final String SYSTEM_ADMIN_AUTHORITY = "ROLE_SYSTEM_ADMIN";

    private final FarmMembershipJpaRepository membershipRepository;

    public FarmAuthorizationService(FarmMembershipJpaRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public CurrentFarmActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !StringUtils.hasText(authentication.getName())) {
            throw new FarmException("UNAUTHENTICATED", "Authentication is required", 401);
        }

        boolean systemAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> SYSTEM_ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return new CurrentFarmActor(authentication.getName(), systemAdmin);
    }

    @Transactional(readOnly = true)
    public void requireAccess(UUID farmId) {
        CurrentFarmActor actor = currentActor();
        if (actor.systemAdmin()) {
            return;
        }
        if (!membershipRepository.existsByFarmIdAndSubject(farmId, actor.subject())) {
            throw new FarmException(
                    "FARM_ACCESS_DENIED",
                    "You do not have access to this farm",
                    403
            );
        }
    }

    public record CurrentFarmActor(String subject, boolean systemAdmin) {
    }
}

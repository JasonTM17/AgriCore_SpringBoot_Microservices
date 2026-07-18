package com.agricore.farm.application.service;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.GrantFarmMembershipRequest;
import com.agricore.farm.api.response.FarmMembershipResponse;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.infrastructure.persistence.FarmJpaRepository;
import com.agricore.farm.infrastructure.persistence.FarmMembershipJpaRepository;
import com.agricore.farm.infrastructure.persistence.entity.FarmMembershipEntity;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FarmMembershipApplicationService {

    private static final String UNIQUE_CONSTRAINT = "uk_farm_memberships_farm_subject";

    private final FarmJpaRepository farmRepository;
    private final FarmMembershipJpaRepository membershipRepository;
    private final FarmAuthorizationService authorizationService;

    public FarmMembershipApplicationService(
            FarmJpaRepository farmRepository,
            FarmMembershipJpaRepository membershipRepository,
            FarmAuthorizationService authorizationService
    ) {
        this.farmRepository = farmRepository;
        this.membershipRepository = membershipRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public void grantCreator(UUID farmId) {
        String subject = authorizationService.currentActor().subject();
        if (!membershipRepository.existsByFarmIdAndSubject(farmId, subject)) {
            saveMembership(farmId, subject, subject);
        }
    }

    @Transactional
    public FarmMembershipResponse grant(UUID farmId, GrantFarmMembershipRequest request) {
        requireManagedFarm(farmId);
        String subject = request.subject().toString();
        if (membershipRepository.existsByFarmIdAndSubject(farmId, subject)) {
            throw membershipExists();
        }

        String grantedBy = authorizationService.currentActor().subject();
        return toResponse(saveMembership(farmId, subject, grantedBy));
    }

    @Transactional(readOnly = true)
    public PageResponse<FarmMembershipResponse> list(UUID farmId, Pageable pageable) {
        requireManagedFarm(farmId);
        Page<FarmMembershipEntity> page = membershipRepository.findByFarmId(farmId, pageable);
        return PageResponse.of(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional
    public void revoke(UUID farmId, UUID membershipId) {
        requireManagedFarm(farmId);
        List<FarmMembershipEntity> memberships = membershipRepository.findByFarmIdForUpdate(farmId);
        FarmMembershipEntity membership = memberships.stream()
                .filter(candidate -> membershipId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new FarmException(
                        "FARM_MEMBERSHIP_NOT_FOUND",
                        "Farm membership not found",
                        404
                ));
        if (memberships.size() <= 1) {
            throw new FarmException(
                    "LAST_FARM_MEMBERSHIP",
                    "Grant a replacement before removing the final farm membership",
                    409
            );
        }
        membershipRepository.delete(membership);
    }

    private void requireManagedFarm(UUID farmId) {
        authorizationService.requireAccess(farmId);
        if (!farmRepository.existsById(farmId)) {
            throw new FarmException("FARM_NOT_FOUND", "Farm not found", 404);
        }
    }

    private FarmMembershipEntity saveMembership(UUID farmId, String subject, String grantedBy) {
        FarmMembershipEntity membership = new FarmMembershipEntity();
        membership.setId(UUID.randomUUID());
        membership.setFarmId(farmId);
        membership.setSubject(subject);
        membership.setGrantedBy(grantedBy);
        membership.setCreatedAt(Instant.now());
        try {
            return membershipRepository.saveAndFlush(membership);
        } catch (DataIntegrityViolationException ex) {
            if (isMembershipUniqueViolation(ex)) {
                throw membershipExists();
            }
            throw ex;
        }
    }

    private boolean isMembershipUniqueViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != current) {
            if (current instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();
                return constraintName != null
                        && constraintName.toLowerCase(Locale.ROOT).contains(UNIQUE_CONSTRAINT);
            }
            current = current.getCause();
        }
        return false;
    }

    private FarmException membershipExists() {
        return new FarmException(
                "FARM_MEMBERSHIP_EXISTS",
                "Subject already has access to this farm",
                409
        );
    }

    private FarmMembershipResponse toResponse(FarmMembershipEntity membership) {
        return new FarmMembershipResponse(
                membership.getId(),
                membership.getFarmId(),
                membership.getSubject(),
                membership.getGrantedBy(),
                membership.getCreatedAt()
        );
    }
}

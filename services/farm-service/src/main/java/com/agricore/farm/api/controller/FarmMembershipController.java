package com.agricore.farm.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.farm.api.request.GrantFarmMembershipRequest;
import com.agricore.farm.api.response.FarmMembershipResponse;
import com.agricore.farm.application.service.FarmMembershipApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/memberships")
@Validated
@PreAuthorize("hasAuthority('PERMISSION_FARM_ADMIN')")
public class FarmMembershipController {

    private final FarmMembershipApplicationService membershipService;

    public FarmMembershipController(FarmMembershipApplicationService membershipService) {
        this.membershipService = membershipService;
    }

    @PostMapping
    public ResponseEntity<FarmMembershipResponse> grant(
            @PathVariable UUID farmId,
            @Valid @RequestBody GrantFarmMembershipRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(membershipService.grant(farmId, request));
    }

    @GetMapping
    public PageResponse<FarmMembershipResponse> list(
            @PathVariable UUID farmId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return membershipService.list(
                farmId,
                PageRequest.of(page, size, Sort.by("createdAt").ascending())
        );
    }

    @DeleteMapping("/{membershipId}")
    public ResponseEntity<Void> revoke(
            @PathVariable UUID farmId,
            @PathVariable UUID membershipId
    ) {
        membershipService.revoke(farmId, membershipId);
        return ResponseEntity.noContent().build();
    }
}

package com.agricore.cropcycle.api.controller;

import com.agricore.common.api.PageResponse;
import com.agricore.cropcycle.api.request.ChangeStageRequest;
import com.agricore.cropcycle.api.request.CreateCropCycleRequest;
import com.agricore.cropcycle.api.request.CreateCropCycleObservationRequest;
import com.agricore.cropcycle.api.response.CropCycleObservationResponse;
import com.agricore.cropcycle.api.response.CropCycleResponse;
import com.agricore.cropcycle.api.response.CropCycleStageHistoryResponse;
import com.agricore.cropcycle.application.service.CropCycleApplicationService;
import com.agricore.cropcycle.application.service.CropCycleObservationService;
import com.agricore.cropcycle.application.service.CropCycleStageHistoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crop-cycles")
@Validated
public class CropCycleController {

    private final CropCycleApplicationService service;
    private final CropCycleStageHistoryService stageHistoryService;
    private final CropCycleObservationService observationService;

    public CropCycleController(
            CropCycleApplicationService service,
            CropCycleStageHistoryService stageHistoryService,
            CropCycleObservationService observationService
    ) {
        this.service = service;
        this.stageHistoryService = stageHistoryService;
        this.observationService = observationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CYCLE_WRITE')")
    public ResponseEntity<CropCycleResponse> create(
            @Valid @RequestBody CreateCropCycleRequest request,
            Principal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, principal.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CYCLE_READ')")
    public PageResponse<CropCycleResponse> list(
            @RequestParam(required = false) UUID farmId,
            @RequestParam(required = false) UUID plotId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.list(farmId, plotId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{cycleId}")
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CYCLE_READ')")
    public CropCycleResponse get(@PathVariable UUID cycleId) {
        return service.get(cycleId);
    }

    @PatchMapping("/{cycleId}/stage")
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CYCLE_WRITE')")
    public CropCycleResponse changeStage(
            @PathVariable UUID cycleId,
            @Valid @RequestBody ChangeStageRequest request,
            Principal principal
    ) {
        return service.changeStage(cycleId, request, principal.getName());
    }

    @Deprecated(forRemoval = false)
    @PostMapping("/{cycleId}/stage")
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CYCLE_WRITE')")
    public CropCycleResponse changeStageLegacy(
            @PathVariable UUID cycleId,
            @Valid @RequestBody ChangeStageRequest request,
            Principal principal
    ) {
        return service.changeStage(cycleId, request, principal.getName());
    }

    @PostMapping("/{cycleId}/cancel")
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CYCLE_WRITE')")
    public CropCycleResponse cancel(@PathVariable UUID cycleId, Principal principal) {
        return service.cancel(cycleId, principal.getName());
    }

    @GetMapping("/{cycleId}/stage-history")
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CYCLE_READ')")
    public PageResponse<CropCycleStageHistoryResponse> listStageHistory(
            @PathVariable UUID cycleId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Sort sort = Sort.by(
                Sort.Order.desc("cycleVersion"),
                Sort.Order.desc("changedAt"),
                Sort.Order.desc("id")
        );
        return stageHistoryService.list(cycleId, PageRequest.of(page, size, sort));
    }

    @PostMapping("/{cycleId}/observations")
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CYCLE_USE')")
    public ResponseEntity<CropCycleObservationResponse> createObservation(
            @PathVariable UUID cycleId,
            @Valid @RequestBody CreateCropCycleObservationRequest request,
            Principal principal
    ) {
        CropCycleObservationResponse observation =
                observationService.create(cycleId, request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(observation);
    }

    @GetMapping("/{cycleId}/observations")
    @PreAuthorize("hasAuthority('PERMISSION_CROP_CYCLE_READ')")
    public PageResponse<CropCycleObservationResponse> listObservations(
            @PathVariable UUID cycleId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Sort sort = Sort.by(
                Sort.Order.desc("observedAt"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
        return observationService.list(cycleId, PageRequest.of(page, size, sort));
    }
}

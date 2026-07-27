package com.agricore.farm.application.service;

import com.agricore.farm.api.request.CreateFarmRequest;
import com.agricore.farm.api.request.CreatePlotRequest;
import com.agricore.farm.api.request.UpdateFarmRequest;
import com.agricore.farm.api.request.UpdatePlotRequest;
import com.agricore.farm.api.response.FarmResponse;
import com.agricore.farm.domain.exception.FarmException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The rejection paths. Farm's only prior tests were two happy-path flows, which left the service at
 * 2 of 38 covered branches — every duplicate check and every missing-row check was unexecuted, so
 * nothing would have noticed if one had returned the wrong status or stopped throwing at all.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "system-admin", roles = "SYSTEM_ADMIN")
class FarmConflictAndNotFoundTest {

    @Autowired
    private FarmApplicationService farmService;

    @Autowired
    private PlotApplicationService plotService;

    @Autowired
    private PlotQueryService plotQueryService;

    @Test
    void duplicateFarmCodeIsRejectedAsAConflict() {
        String code = "DUP-" + System.nanoTime();
        farmService.createFarm(farmRequest(code));

        FarmException thrown = catchThrowableOfType(
                () -> farmService.createFarm(farmRequest(code)), FarmException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getCode()).isEqualTo("FARM_CODE_EXISTS");
        assertThat(thrown.getHttpStatus()).isEqualTo(409);
    }

    /**
     * The check is {@code existsByCodeIgnoreCase} and the code is uppercased before insert, so a
     * lowercase resubmission of the same code must still collide rather than create a second farm.
     */
    @Test
    void farmCodeComparisonIgnoresCase() {
        String code = "MiXeD-" + System.nanoTime();
        farmService.createFarm(farmRequest(code.toUpperCase()));

        FarmException thrown = catchThrowableOfType(
                () -> farmService.createFarm(farmRequest(code.toLowerCase())), FarmException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getCode()).isEqualTo("FARM_CODE_EXISTS");
    }

    @Test
    void plotCodeIsUniqueWithinItsFarmOnly() {
        FarmResponse first = farmService.createFarm(farmRequest("PU1-" + System.nanoTime()));
        FarmResponse second = farmService.createFarm(farmRequest("PU2-" + System.nanoTime()));
        plotService.create(first.id(), plotRequest("P-01"));

        FarmException thrown = catchThrowableOfType(
                () -> plotService.create(first.id(), plotRequest("P-01")), FarmException.class);
        assertThat(thrown).isNotNull();
        assertThat(thrown.getCode()).isEqualTo("PLOT_CODE_EXISTS");
        assertThat(thrown.getHttpStatus()).isEqualTo(409);

        // The same code under a different farm is not a conflict; the index is (farm_id, code).
        assertThat(plotService.create(second.id(), plotRequest("P-01")).code()).isEqualTo("P-01");
    }

    @Test
    void everyFarmLookupReportsAMissingFarmAsNotFound() {
        UUID missing = UUID.randomUUID();

        for (Runnable call : new Runnable[]{
                () -> farmService.getFarm(missing),
                () -> farmService.updateFarm(missing, farmNamePatch(0, "x")),
                () -> plotService.create(missing, plotRequest("P-01")),
                () -> plotQueryService.list(missing, null, null, null, PageRequest.of(0, 20)),
        }) {
            FarmException thrown = catchThrowableOfType(call::run, FarmException.class);
            assertThat(thrown).isNotNull();
            assertThat(thrown.getCode()).isEqualTo("FARM_NOT_FOUND");
            assertThat(thrown.getHttpStatus()).isEqualTo(404);
        }
    }

    /**
     * Plot creation checks the farm exists before it checks the plot code. A missing farm must
     * report FARM_NOT_FOUND rather than falling through to a plot-level answer.
     */
    @Test
    void missingFarmIsReportedBeforeThePlotCodeCheck() {
        FarmException thrown = catchThrowableOfType(
                () -> plotService.create(UUID.randomUUID(), plotRequest("P-01")), FarmException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getCode()).isEqualTo("FARM_NOT_FOUND");
    }

    @Test
    void everyPlotLookupReportsAMissingPlotAsNotFound() {
        UUID missing = UUID.randomUUID();

        for (Runnable call : new Runnable[]{
                () -> plotService.get(missing),
                () -> plotService.update(missing, plotPatch(0)),
        }) {
            FarmException thrown = catchThrowableOfType(call::run, FarmException.class);
            assertThat(thrown).isNotNull();
            assertThat(thrown.getCode()).isEqualTo("PLOT_NOT_FOUND");
            assertThat(thrown.getHttpStatus()).isEqualTo(404);
        }
    }

    /**
     * Status arrives as a caller-supplied string and reaches {@code FarmStatus.valueOf}. An unknown
     * value must not escape as something the advice cannot classify; farm maps
     * IllegalArgumentException to 400.
     */
    @Test
    void anUnknownStatusFiltersAsAnIllegalArgument() {
        assertThat(catchThrowableOfType(
                () -> farmService.listFarms(null, "NOT_A_STATUS", null, PageRequest.of(0, 20)),
                IllegalArgumentException.class))
                .isNotNull();
    }

    private static UpdateFarmRequest farmNamePatch(long version, String name) {
        UpdateFarmRequest request = new UpdateFarmRequest();
        request.setVersion(version);
        request.setName(name);
        return request;
    }

    private static UpdatePlotRequest plotPatch(long version) {
        UpdatePlotRequest request = new UpdatePlotRequest();
        request.setVersion(version);
        return request;
    }

    private static CreateFarmRequest farmRequest(String code) {
        return new CreateFarmRequest(
                code, "Test Farm", null, "Dak Lak", "Dak Lak",
                new BigDecimal("10.5"), 12.6667, 108.05);
    }

    private static CreatePlotRequest plotRequest(String code) {
        return new CreatePlotRequest(
                code, "Block", new BigDecimal("2.5"), "BASALT", null, 12.67, 108.05);
    }
}

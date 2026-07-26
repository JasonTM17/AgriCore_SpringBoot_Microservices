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
class FarmConflictAndNotFoundTest {

    @Autowired
    private FarmApplicationService service;

    @Test
    void duplicateFarmCodeIsRejectedAsAConflict() {
        String code = "DUP-" + System.nanoTime();
        service.createFarm(farmRequest(code));

        FarmException thrown = catchThrowableOfType(
                () -> service.createFarm(farmRequest(code)), FarmException.class);

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
        service.createFarm(farmRequest(code.toUpperCase()));

        FarmException thrown = catchThrowableOfType(
                () -> service.createFarm(farmRequest(code.toLowerCase())), FarmException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getCode()).isEqualTo("FARM_CODE_EXISTS");
    }

    @Test
    void plotCodeIsUniqueWithinItsFarmOnly() {
        FarmResponse first = service.createFarm(farmRequest("PU1-" + System.nanoTime()));
        FarmResponse second = service.createFarm(farmRequest("PU2-" + System.nanoTime()));
        service.createPlot(first.id(), plotRequest("P-01"));

        FarmException thrown = catchThrowableOfType(
                () -> service.createPlot(first.id(), plotRequest("P-01")), FarmException.class);
        assertThat(thrown).isNotNull();
        assertThat(thrown.getCode()).isEqualTo("PLOT_CODE_EXISTS");
        assertThat(thrown.getHttpStatus()).isEqualTo(409);

        // The same code under a different farm is not a conflict; the index is (farm_id, code).
        assertThat(service.createPlot(second.id(), plotRequest("P-01")).code()).isEqualTo("P-01");
    }

    @Test
    void everyFarmLookupReportsAMissingFarmAsNotFound() {
        UUID missing = UUID.randomUUID();

        for (Runnable call : new Runnable[]{
                () -> service.getFarm(missing),
                () -> service.updateFarm(missing, new UpdateFarmRequest("x", null, null, null, null, null, null)),
                () -> service.createPlot(missing, plotRequest("P-01")),
                () -> service.listPlots(missing, PageRequest.of(0, 20)),
        }) {
            FarmException thrown = catchThrowableOfType(call::run, FarmException.class);
            assertThat(thrown).isNotNull();
            assertThat(thrown.getCode()).isEqualTo("FARM_NOT_FOUND");
            assertThat(thrown.getHttpStatus()).isEqualTo(404);
        }
    }

    /**
     * createPlot checks the farm exists before it checks the plot code. A missing farm must report
     * FARM_NOT_FOUND rather than falling through to a plot-level answer.
     */
    @Test
    void missingFarmIsReportedBeforeThePlotCodeCheck() {
        FarmException thrown = catchThrowableOfType(
                () -> service.createPlot(UUID.randomUUID(), plotRequest("P-01")), FarmException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getCode()).isEqualTo("FARM_NOT_FOUND");
    }

    @Test
    void everyPlotLookupReportsAMissingPlotAsNotFound() {
        UUID missing = UUID.randomUUID();

        for (Runnable call : new Runnable[]{
                () -> service.getPlot(missing),
                () -> service.updatePlot(missing, new UpdatePlotRequest(null, null, null, null, null, null)),
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
                () -> service.listFarms(null, "NOT_A_STATUS", PageRequest.of(0, 20)),
                IllegalArgumentException.class))
                .isNotNull();
    }

    private static CreateFarmRequest farmRequest(String code) {
        return new CreateFarmRequest(
                code, "Test Farm", "Dak Lak", "Dak Lak",
                new BigDecimal("10.5"), 12.6667, 108.05);
    }

    private static CreatePlotRequest plotRequest(String code) {
        return new CreatePlotRequest(
                code, "Block", new BigDecimal("2.5"), "BASALT", null, 12.67, 108.05);
    }
}

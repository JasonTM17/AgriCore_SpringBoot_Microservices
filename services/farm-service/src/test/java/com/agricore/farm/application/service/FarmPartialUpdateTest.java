package com.agricore.farm.application.service;

import com.agricore.farm.api.request.CreateFarmRequest;
import com.agricore.farm.api.request.UpdateFarmRequest;
import com.agricore.farm.api.response.FarmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PATCH semantics. {@code updateFarm} is seven independent null checks, and every one of them was
 * unexecuted — so nothing established that sending one field leaves the other six alone, which is
 * the whole contract of a partial update.
 *
 * <p>The failure this guards against is not exotic: dropping a null check turns an omitted field
 * into a silent overwrite with null, and the endpoint keeps answering 200.
 */
@SpringBootTest
@ActiveProfiles("test")
class FarmPartialUpdateTest {

    @Autowired
    private FarmApplicationService service;

    @Test
    void updatingOneFieldLeavesTheRestUntouched() {
        FarmResponse created = service.createFarm(fullFarm());

        FarmResponse updated = service.updateFarm(created.id(),
                new UpdateFarmRequest("Renamed", null, null, null, null, null, null));

        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.address()).isEqualTo(created.address());
        assertThat(updated.province()).isEqualTo(created.province());
        assertThat(updated.totalAreaHa()).isEqualByComparingTo(created.totalAreaHa());
        assertThat(updated.latitude()).isEqualTo(created.latitude());
        assertThat(updated.longitude()).isEqualTo(created.longitude());
        assertThat(updated.status()).isEqualTo(created.status());
    }

    @Test
    void anEmptyPatchChangesNothingButTheTimestamp() {
        FarmResponse created = service.createFarm(fullFarm());

        FarmResponse updated = service.updateFarm(created.id(),
                new UpdateFarmRequest(null, null, null, null, null, null, null));

        assertThat(updated.name()).isEqualTo(created.name());
        assertThat(updated.address()).isEqualTo(created.address());
        assertThat(updated.province()).isEqualTo(created.province());
        assertThat(updated.status()).isEqualTo(created.status());
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
    }

    /**
     * Each field applied on its own, so a null check wired to the wrong setter is caught rather
     * than hidden by a test that sends everything at once.
     */
    @Test
    void eachFieldIsIndividuallyApplied() {
        FarmResponse farm = service.createFarm(fullFarm());

        assertThat(patch(farm, new UpdateFarmRequest(null, "New address", null, null, null, null, null)).address())
                .isEqualTo("New address");
        assertThat(patch(farm, new UpdateFarmRequest(null, null, "Lam Dong", null, null, null, null)).province())
                .isEqualTo("Lam Dong");
        assertThat(patch(farm, new UpdateFarmRequest(null, null, null, new BigDecimal("77.25"), null, null, null)).totalAreaHa())
                .isEqualByComparingTo("77.25");
        assertThat(patch(farm, new UpdateFarmRequest(null, null, null, null, 10.5, null, null)).latitude())
                .isEqualTo(10.5);
        assertThat(patch(farm, new UpdateFarmRequest(null, null, null, null, null, 106.5, null)).longitude())
                .isEqualTo(106.5);
        assertThat(patch(farm, new UpdateFarmRequest(null, null, null, null, null, null, "INACTIVE")).status())
                .isEqualTo("INACTIVE");
    }

    /**
     * Status is a caller-supplied string fed to {@code FarmStatus.valueOf}, so it must accept the
     * lowercase form a hand-written client is likely to send.
     */
    @Test
    void statusIsAcceptedInAnyCase() {
        FarmResponse farm = service.createFarm(fullFarm());

        assertThat(patch(farm, new UpdateFarmRequest(null, null, null, null, null, null, "inactive")).status())
                .isEqualTo("INACTIVE");
    }

    private FarmResponse patch(FarmResponse farm, UpdateFarmRequest request) {
        return service.updateFarm(farm.id(), request);
    }

    private static CreateFarmRequest fullFarm() {
        return new CreateFarmRequest(
                "PATCH-" + System.nanoTime(),
                "Original Name",
                "Original address",
                "Dak Lak",
                new BigDecimal("120.50"),
                12.6667,
                108.05);
    }
}

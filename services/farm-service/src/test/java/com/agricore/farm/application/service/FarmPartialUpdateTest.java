package com.agricore.farm.application.service;

import com.agricore.farm.api.request.CreateFarmRequest;
import com.agricore.farm.api.request.UpdateFarmRequest;
import com.agricore.farm.api.response.FarmResponse;
import com.agricore.farm.domain.exception.FarmException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * PATCH semantics. {@code updateFarm} applies only fields explicitly marked present by the request
 * setters. These tests establish that sending one field leaves every omitted field alone, which is
 * the core contract of a partial update.
 *
 * <p>The failure this guards against is not exotic: dropping a null check turns an omitted field
 * into a silent overwrite with null, and the endpoint keeps answering 200.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "system-admin", roles = "SYSTEM_ADMIN")
class FarmPartialUpdateTest {

    @Autowired
    private FarmApplicationService service;

    @Test
    void updatingOneFieldLeavesTheRestUntouched() {
        FarmResponse created = service.createFarm(fullFarm());

        FarmResponse updated = patch(created, request -> request.setName("Renamed"));

        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.enterpriseId()).isEqualTo(created.enterpriseId());
        assertThat(updated.address()).isEqualTo(created.address());
        assertThat(updated.province()).isEqualTo(created.province());
        assertThat(updated.totalAreaHa()).isEqualByComparingTo(created.totalAreaHa());
        assertThat(updated.latitude()).isEqualTo(created.latitude());
        assertThat(updated.longitude()).isEqualTo(created.longitude());
        assertThat(updated.status()).isEqualTo(created.status());
    }

    @Test
    void anEmptyPatchIsRejectedWithoutChangingTheFarm() {
        FarmResponse created = service.createFarm(fullFarm());
        FarmResponse beforeUpdate = service.getFarm(created.id());
        UpdateFarmRequest request = new UpdateFarmRequest();
        request.setVersion(beforeUpdate.version());

        FarmException thrown = catchThrowableOfType(
                () -> service.updateFarm(created.id(), request),
                FarmException.class);
        FarmResponse unchanged = service.getFarm(created.id());

        assertThat(thrown).isNotNull();
        assertThat(thrown.getCode()).isEqualTo("FARM_EMPTY_UPDATE");
        assertThat(thrown.getHttpStatus()).isEqualTo(400);
        assertThat(unchanged).isEqualTo(beforeUpdate);
    }

    /**
     * Each field is applied on its own, so a presence check wired to the wrong setter is caught
     * rather than hidden by a test that sends everything at once.
     */
    @Test
    void eachFieldIsIndividuallyApplied() {
        FarmResponse farm = service.createFarm(fullFarm());

        farm = patch(farm, request -> request.setAddress("New address"));
        assertThat(farm.address()).isEqualTo("New address");

        farm = patch(farm, request -> request.setProvince("Lam Dong"));
        assertThat(farm.province()).isEqualTo("Lam Dong");

        farm = patch(farm, request -> request.setTotalAreaHa(new BigDecimal("77.25")));
        assertThat(farm.totalAreaHa()).isEqualByComparingTo("77.25");

        farm = patch(farm, request -> request.setLatitude(10.5));
        assertThat(farm.latitude()).isEqualTo(10.5);

        farm = patch(farm, request -> request.setLongitude(106.5));
        assertThat(farm.longitude()).isEqualTo(106.5);

        farm = patch(farm, request -> request.setStatus("INACTIVE"));
        assertThat(farm.status()).isEqualTo("INACTIVE");
    }

    /**
     * Status is a caller-supplied string fed to {@code FarmStatus.valueOf}, so it must accept the
     * lowercase form a hand-written client is likely to send.
     */
    @Test
    void statusIsAcceptedInAnyCase() {
        FarmResponse farm = service.createFarm(fullFarm());

        assertThat(patch(farm, request -> request.setStatus("inactive")).status())
                .isEqualTo("INACTIVE");
    }

    private FarmResponse patch(FarmResponse farm, Consumer<UpdateFarmRequest> update) {
        UpdateFarmRequest request = new UpdateFarmRequest();
        request.setVersion(farm.version());
        update.accept(request);
        return service.updateFarm(farm.id(), request);
    }

    private static CreateFarmRequest fullFarm() {
        return new CreateFarmRequest(
                "PATCH-" + System.nanoTime(),
                "Original Name",
                null,
                "Original address",
                "Dak Lak",
                new BigDecimal("120.50"),
                12.6667,
                108.05);
    }
}

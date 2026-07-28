package com.agricore.cropcycle;

import com.agricore.common.event.EventTypes;
import com.agricore.cropcycle.api.request.CreateCropCycleRequest;
import com.agricore.cropcycle.application.service.CropCycleApplicationService;
import com.agricore.cropcycle.application.service.CropCycleOutboxWriter;
import com.agricore.cropcycle.domain.exception.CropCycleException;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleJpaRepository;
import com.agricore.farmaccess.FarmAccessClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class CropCycleOutboxRollbackTest {

    private static final String ACTOR = "outbox-rollback-test";

    @Autowired
    private CropCycleApplicationService cycleService;
    @Autowired
    private CropCycleJpaRepository cycleRepository;
    @MockitoBean
    private CropCycleOutboxWriter outboxWriter;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void outboxFailure_rollsBackCreatedCycle() {
        String code = "OBX-ROLLBACK-" + UUID.randomUUID();
        CreateCropCycleRequest request = new CreateCropCycleRequest(
                code,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 11, 30),
                null
        );
        CropCycleException failure = new CropCycleException(
                "OUTBOX_WRITE_FAILED",
                "Failed to write outbox event",
                500
        );
        doThrow(failure).when(outboxWriter).enqueue(
                eq(EventTypes.CROP_CYCLE_CREATED),
                any(),
                isNull()
        );

        assertThatThrownBy(() -> cycleService.create(request, ACTOR)).isSameAs(failure);

        assertThat(cycleRepository.existsByCodeIgnoreCase(code)).isFalse();
    }
}

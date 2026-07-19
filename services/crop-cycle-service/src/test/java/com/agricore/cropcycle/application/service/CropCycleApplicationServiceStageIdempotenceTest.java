package com.agricore.cropcycle.application.service;

import com.agricore.cropcycle.api.request.ChangeStageRequest;
import com.agricore.cropcycle.api.response.CropCycleResponse;
import com.agricore.cropcycle.domain.exception.CropCycleException;
import com.agricore.cropcycle.domain.model.CycleStage;
import com.agricore.cropcycle.domain.model.CycleStatus;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleEntity;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CropCycleApplicationServiceStageIdempotenceTest {

    @Mock
    private CropCycleJpaRepository cycleRepository;
    @Mock
    private CropCycleOutboxWriter outboxWriter;
    @Mock
    private CropCycleAccessGuard accessGuard;
    @InjectMocks
    private CropCycleApplicationService service;

    @ParameterizedTest
    @EnumSource(value = CycleStage.class, names = {"COMPLETED", "CANCELLED"})
    void terminalSameStage_returnsCurrentStateWithoutWriteOrEvent(CycleStage stage) {
        CropCycleEntity cycle = terminalCycle(stage);
        when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));

        CropCycleResponse response = service.changeStage(
                cycle.getId(),
                new ChangeStageRequest(stage.name(), null)
        );

        assertThat(response.stage()).isEqualTo(stage.name());
        assertThat(response.status()).isEqualTo(CycleStatus.valueOf(stage.name()).name());
        assertThat(response.version()).isEqualTo(cycle.getVersion());
        verify(accessGuard).requireFarmPlot(cycle.getFarmId(), cycle.getPlotId());
        verify(cycleRepository).findById(cycle.getId());
        verifyNoMoreInteractions(cycleRepository);
        verifyNoInteractions(outboxWriter);
    }

    @ParameterizedTest
    @CsvSource({"COMPLETED,CANCELLED", "CANCELLED,COMPLETED", "COMPLETED,NOT_A_STAGE"})
    void terminalDifferentOrInvalidStage_remainsRejectedWithoutWriteOrEvent(
            CycleStage currentStage,
            String requestedStage
    ) {
        CropCycleEntity cycle = terminalCycle(currentStage);
        when(cycleRepository.findById(cycle.getId())).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> service.changeStage(
                cycle.getId(),
                new ChangeStageRequest(requestedStage, null)
        )).isInstanceOfSatisfying(CropCycleException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo("CYCLE_TERMINAL");
            assertThat(exception.getHttpStatus()).isEqualTo(409);
        });

        verify(accessGuard).requireFarmPlot(cycle.getFarmId(), cycle.getPlotId());
        verify(cycleRepository).findById(cycle.getId());
        verifyNoMoreInteractions(cycleRepository);
        verifyNoInteractions(outboxWriter);
    }

    private CropCycleEntity terminalCycle(CycleStage stage) {
        Instant now = Instant.parse("2026-07-19T00:00:00Z");
        CropCycleEntity cycle = new CropCycleEntity();
        cycle.setId(UUID.randomUUID());
        cycle.setCode("TERMINAL-" + stage.name());
        cycle.setFarmId(UUID.randomUUID());
        cycle.setPlotId(UUID.randomUUID());
        cycle.setCropId(UUID.randomUUID());
        cycle.setPlannedStartDate(LocalDate.parse("2026-03-01"));
        cycle.setStage(stage);
        cycle.setStatus(CycleStatus.valueOf(stage.name()));
        cycle.setCreatedAt(now);
        cycle.setUpdatedAt(now);
        return cycle;
    }
}

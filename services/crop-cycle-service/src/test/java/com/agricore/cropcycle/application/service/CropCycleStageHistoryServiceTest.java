package com.agricore.cropcycle.application.service;

import com.agricore.cropcycle.domain.exception.CropCycleException;
import com.agricore.cropcycle.domain.model.CycleStage;
import com.agricore.cropcycle.domain.model.CycleStatus;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleStageHistoryJpaRepository;
import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleEntity;
import com.agricore.cropcycle.infrastructure.persistence.entity.CropCycleStageHistoryEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CropCycleStageHistoryServiceTest {

    @Mock
    private CropCycleStageHistoryJpaRepository historyRepository;
    @Mock
    private CropCycleJpaRepository cycleRepository;
    @Mock
    private CropCycleAccessGuard accessGuard;
    @InjectMocks
    private CropCycleStageHistoryService service;

    @Test
    void record_normalizesActorAndCapturesCommittedCycleVersion() {
        CropCycleEntity cycle = cycle();

        service.record(cycle, CycleStage.PLANNED, "  manager-a  ", "Beds prepared");

        ArgumentCaptor<CropCycleStageHistoryEntity> history =
                ArgumentCaptor.forClass(CropCycleStageHistoryEntity.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getCropCycleId()).isEqualTo(cycle.getId());
        assertThat(history.getValue().getPreviousStage()).isEqualTo("PLANNED");
        assertThat(history.getValue().getStage()).isEqualTo("LAND_PREPARATION");
        assertThat(history.getValue().getChangedBy()).isEqualTo("manager-a");
        assertThat(history.getValue().getCycleVersion()).isZero();
        verifyNoInteractions(cycleRepository, accessGuard);
    }

    @Test
    void record_rejectsInvalidActorWithoutAuditWrite() {
        CropCycleEntity cycle = cycle();

        assertInvalidActor(cycle, "   ");
        assertInvalidActor(cycle, "a".repeat(256));
        verify(historyRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void assertInvalidActor(CropCycleEntity cycle, String actor) {
        assertThatThrownBy(() -> service.record(cycle, CycleStage.PLANNED, actor, null))
                .isInstanceOfSatisfying(CropCycleException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_AUTHENTICATED_ACTOR");
                    assertThat(exception.getHttpStatus()).isEqualTo(401);
                });
    }

    private CropCycleEntity cycle() {
        CropCycleEntity cycle = new CropCycleEntity();
        cycle.setId(UUID.randomUUID());
        cycle.setStage(CycleStage.LAND_PREPARATION);
        cycle.setStatus(CycleStatus.ACTIVE);
        cycle.setUpdatedAt(Instant.parse("2026-07-22T10:00:00Z"));
        return cycle;
    }
}

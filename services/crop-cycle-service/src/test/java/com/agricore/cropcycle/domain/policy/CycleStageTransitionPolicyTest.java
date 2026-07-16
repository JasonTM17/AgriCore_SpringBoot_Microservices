package com.agricore.cropcycle.domain.policy;

import com.agricore.cropcycle.domain.model.CycleStage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CycleStageTransitionPolicyTest {

    @Test
    void plannedMayGoToLandPreparationButNotCompleted() {
        assertThat(CycleStageTransitionPolicy.canTransition(CycleStage.PLANNED, CycleStage.LAND_PREPARATION)).isTrue();
        assertThat(CycleStageTransitionPolicy.canTransition(CycleStage.PLANNED, CycleStage.COMPLETED)).isFalse();
        assertThat(CycleStageTransitionPolicy.canTransition(CycleStage.PLANNED, CycleStage.SOWING)).isFalse();
    }

    @Test
    void harvestingMayComplete() {
        assertThat(CycleStageTransitionPolicy.canTransition(CycleStage.HARVESTING, CycleStage.COMPLETED)).isTrue();
    }
}

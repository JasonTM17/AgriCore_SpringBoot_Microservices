package com.agricore.cropcycle.domain.policy;

import com.agricore.cropcycle.domain.model.CycleStage;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Legal crop-cycle stage transitions (domain policy, no Spring).
 */
public final class CycleStageTransitionPolicy {

    private static final Map<CycleStage, Set<CycleStage>> ALLOWED = new EnumMap<>(CycleStage.class);

    static {
        ALLOWED.put(CycleStage.PLANNED, EnumSet.of(CycleStage.LAND_PREPARATION, CycleStage.CANCELLED));
        ALLOWED.put(CycleStage.LAND_PREPARATION, EnumSet.of(CycleStage.SOWING, CycleStage.CANCELLED));
        ALLOWED.put(CycleStage.SOWING, EnumSet.of(CycleStage.GROWING, CycleStage.CANCELLED));
        ALLOWED.put(CycleStage.GROWING, EnumSet.of(
                CycleStage.FERTILIZING, CycleStage.PEST_CONTROL, CycleStage.HARVESTING, CycleStage.CANCELLED));
        ALLOWED.put(CycleStage.FERTILIZING, EnumSet.of(
                CycleStage.GROWING, CycleStage.PEST_CONTROL, CycleStage.HARVESTING, CycleStage.CANCELLED));
        ALLOWED.put(CycleStage.PEST_CONTROL, EnumSet.of(
                CycleStage.GROWING, CycleStage.FERTILIZING, CycleStage.HARVESTING, CycleStage.CANCELLED));
        ALLOWED.put(CycleStage.HARVESTING, EnumSet.of(CycleStage.COMPLETED, CycleStage.CANCELLED));
        ALLOWED.put(CycleStage.COMPLETED, EnumSet.noneOf(CycleStage.class));
        ALLOWED.put(CycleStage.CANCELLED, EnumSet.noneOf(CycleStage.class));
    }

    private CycleStageTransitionPolicy() {
    }

    public static boolean canTransition(CycleStage from, CycleStage to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(CycleStage.class)).contains(to);
    }

    public static Set<CycleStage> allowedNext(CycleStage from) {
        return Set.copyOf(ALLOWED.getOrDefault(from, EnumSet.noneOf(CycleStage.class)));
    }
}

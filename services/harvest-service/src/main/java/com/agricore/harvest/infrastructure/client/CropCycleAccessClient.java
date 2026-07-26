package com.agricore.harvest.infrastructure.client;

import java.util.UUID;

public interface CropCycleAccessClient {

    void requireCycle(UUID cropCycleId, UUID farmId, UUID plotId);
}

package com.agricore.harvest.application.service;

import com.agricore.farmaccess.FarmAccessClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
final class HarvestAccessGuard {

    private final FarmAccessClient farmAccessClient;

    HarvestAccessGuard(FarmAccessClient farmAccessClient) {
        this.farmAccessClient = farmAccessClient;
    }

    void requirePlot(UUID plotId) {
        farmAccessClient.requirePlot(plotId);
    }
}

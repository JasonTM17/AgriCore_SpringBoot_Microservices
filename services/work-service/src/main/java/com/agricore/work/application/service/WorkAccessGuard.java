package com.agricore.work.application.service;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
final class WorkAccessGuard {

    private final FarmAccessClient farmAccessClient;

    WorkAccessGuard(FarmAccessClient farmAccessClient) {
        this.farmAccessClient = farmAccessClient;
    }

    void requirePlot(UUID plotId) {
        farmAccessClient.requirePlot(plotId);
    }

    void requireListScope(UUID plotId) {
        if (plotId != null) {
            farmAccessClient.requirePlot(plotId);
            return;
        }
        if (!farmAccessClient.isSystemAdmin()) {
            throw FarmAccessException.scopeRequired();
        }
    }
}

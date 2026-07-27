package com.agricore.iot.application.service;

import com.agricore.farmaccess.FarmAccessClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
final class IotAccessGuard {

    private final FarmAccessClient farmAccessClient;

    IotAccessGuard(FarmAccessClient farmAccessClient) {
        this.farmAccessClient = farmAccessClient;
    }

    void requirePlot(UUID plotId) {
        farmAccessClient.requirePlot(plotId);
    }
}

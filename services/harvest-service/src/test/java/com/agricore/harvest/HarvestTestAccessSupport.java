package com.agricore.harvest;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmResourceAccess;
import org.mockito.Mockito;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

final class HarvestTestAccessSupport {

    static final UUID FARM_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private HarvestTestAccessSupport() {
    }

    static void authorizeAllPlots(FarmAccessClient farmAccessClient) {
        when(farmAccessClient.requirePlot(any(UUID.class)))
                .thenAnswer(invocation -> new FarmResourceAccess(
                        FARM_ID,
                        invocation.getArgument(0, UUID.class)
                ));
    }

    static void authorizePlot(FarmAccessClient farmAccessClient, UUID plotId) {
        Mockito.doReturn(new FarmResourceAccess(FARM_ID, plotId))
                .when(farmAccessClient)
                .requirePlot(plotId);
    }
}

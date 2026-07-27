package com.agricore.sales.application.service;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.sales.domain.exception.SalesException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
final class SalesAccessGuard {

    private final FarmAccessClient farmAccessClient;

    SalesAccessGuard(FarmAccessClient farmAccessClient) {
        this.farmAccessClient = farmAccessClient;
    }

    void requireFarm(UUID farmId) {
        if (farmId == null) {
            throw new SalesException(
                    "ORDER_SCOPE_UNAVAILABLE",
                    "Sales order farm scope is unavailable",
                    503
            );
        }
        farmAccessClient.requireFarm(farmId);
    }
}

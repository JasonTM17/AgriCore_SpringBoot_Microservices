package com.agricore.assistant.infrastructure.farm;

import com.agricore.assistant.application.port.ConversationContextAccess;
import com.agricore.farmaccess.FarmAccessClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FarmConversationContextAccess implements ConversationContextAccess {

    private final FarmAccessClient farmAccessClient;

    public FarmConversationContextAccess(FarmAccessClient farmAccessClient) {
        this.farmAccessClient = farmAccessClient;
    }

    @Override
    public void requireFarmAccess(UUID farmId) {
        farmAccessClient.requireFarm(farmId);
    }
}

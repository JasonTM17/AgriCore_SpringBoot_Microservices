package com.agricore.assistant.application.port;

import com.agricore.assistant.domain.model.AssistantActor;

public interface AssistantRequestBudget {

    /**
     * Atomically reserves the request and the desired total token ceiling for one idempotent attempt.
     * Repeating the same reservation with a larger total only charges the token delta.
     */
    void reserve(AssistantActor actor, String clientIp, String reservationId, int desiredTotalTokens);
}

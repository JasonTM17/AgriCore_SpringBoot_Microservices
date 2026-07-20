package com.agricore.assistant.application.port;

import java.util.UUID;

public interface GenerationWorkDispatcher {

    void dispatchAfterCommit(UUID generationId);
}

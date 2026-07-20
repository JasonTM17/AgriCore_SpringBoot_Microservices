package com.agricore.assistant.application.port;

import java.util.UUID;

public interface GenerationWorkDispatcher {

    void dispatch(UUID generationId);

    void dispatchAfterCommit(UUID generationId);

    void cancelAfterCommit(UUID generationId);
}

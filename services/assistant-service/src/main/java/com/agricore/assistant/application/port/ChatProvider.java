package com.agricore.assistant.application.port;

import com.agricore.assistant.application.model.ChatChunk;
import com.agricore.assistant.application.model.ChatGenerationRequest;
import com.agricore.assistant.application.model.ProviderCapabilities;
import reactor.core.publisher.Flux;

/**
 * Provider-neutral boundary. Provider SDK types must not cross this interface.
 */
public interface ChatProvider {
    ProviderCapabilities capabilities();

    Flux<ChatChunk> stream(ChatGenerationRequest request);
}

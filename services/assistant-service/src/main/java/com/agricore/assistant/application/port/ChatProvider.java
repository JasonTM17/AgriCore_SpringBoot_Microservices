package com.agricore.assistant.application.port;

import com.agricore.assistant.application.model.ProviderCapabilities;

/**
 * Provider-neutral boundary. Provider SDK types must not cross this interface.
 */
public interface ChatProvider {
    ProviderCapabilities capabilities();
}

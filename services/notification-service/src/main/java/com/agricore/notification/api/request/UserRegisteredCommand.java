package com.agricore.notification.api.request;

import java.util.List;

/**
 * Application-layer input parsed from a UserRegistered.v1 envelope.
 * Keeps JSON handling in the listener and domain work in the service.
 */
public record UserRegisteredCommand(
        String eventId,
        String userId,
        String email,
        String fullName,
        List<String> roles
) {
}

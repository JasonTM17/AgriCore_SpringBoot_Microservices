package com.agricore.notification.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendNotificationRequest(
        @NotBlank String channel,
        @NotBlank @Size(max = 320) String recipient,
        @NotBlank @Size(max = 300) String subject,
        @NotBlank String body,
        String correlationId
) {
}

package com.agricore.notification.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendNotificationRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "EMAIL|SMS|IN_APP") String channel,
        @NotBlank @Size(max = 320) String recipient,
        @NotBlank @Size(max = 300) String subject,
        @NotBlank @Size(max = 10000) String body,
        @Size(max = 100) String correlationId
) {
}

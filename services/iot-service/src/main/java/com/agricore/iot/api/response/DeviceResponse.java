package com.agricore.iot.api.response;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(UUID id, String deviceCode, UUID plotId, String name, String status, Instant lastSeenAt) {
}

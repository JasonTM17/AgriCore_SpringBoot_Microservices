package com.agricore.iot.infrastructure.messaging;

import com.agricore.iot.application.service.IotDeviceStatusService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "agricore.device.offline-detection.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DeviceOfflineDetector {

    private final IotDeviceStatusService statusService;

    public DeviceOfflineDetector(IotDeviceStatusService statusService) {
        this.statusService = statusService;
    }

    @Scheduled(fixedDelayString = "${agricore.device.offline-detection.poll-ms:60000}")
    public void detectOfflineDevices() {
        statusService.markOfflineDevices();
    }
}

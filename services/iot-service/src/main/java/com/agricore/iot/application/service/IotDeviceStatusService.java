package com.agricore.iot.application.service;

import com.agricore.iot.infrastructure.persistence.DeviceJpaRepository;
import com.agricore.iot.infrastructure.persistence.entity.DeviceEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class IotDeviceStatusService {

    private static final int DETECTION_BATCH_SIZE = 50;

    private final DeviceJpaRepository deviceRepository;
    private final IotEventOutboxWriter eventWriter;
    private final Duration offlineAfter;

    public IotDeviceStatusService(
            DeviceJpaRepository deviceRepository,
            IotEventOutboxWriter eventWriter,
            @Value("${agricore.device.offline-detection.offline-after:15m}") Duration offlineAfter
    ) {
        if (offlineAfter.isZero() || offlineAfter.isNegative()) {
            throw new IllegalArgumentException("Device offline threshold must be positive");
        }
        this.deviceRepository = deviceRepository;
        this.eventWriter = eventWriter;
        this.offlineAfter = offlineAfter;
    }

    @Transactional
    public int markOfflineDevices() {
        Instant detectedAt = Instant.now();
        Instant cutoff = detectedAt.minus(offlineAfter);
        List<DeviceEntity> staleDevices = deviceRepository.findStaleActiveForUpdate(
                cutoff,
                PageRequest.of(0, DETECTION_BATCH_SIZE)
        );
        for (DeviceEntity device : staleDevices) {
            Instant lastActivityAt = device.getLastSeenAt() == null
                    ? device.getCreatedAt()
                    : device.getLastSeenAt();
            device.setStatus("OFFLINE");
            deviceRepository.save(device);
            eventWriter.deviceOfflineDetected(device, lastActivityAt, detectedAt, offlineAfter);
        }
        return staleDevices.size();
    }
}

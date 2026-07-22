package com.agricore.iot.application.service;

import com.agricore.iot.api.request.IngestReadingRequest;
import com.agricore.iot.api.request.RegisterDeviceRequest;
import com.agricore.iot.api.response.DeviceResponse;
import com.agricore.iot.api.response.IngestResultResponse;
import com.agricore.iot.domain.exception.IotException;
import com.agricore.iot.infrastructure.persistence.DeviceJpaRepository;
import com.agricore.iot.infrastructure.persistence.SensorAlertJpaRepository;
import com.agricore.iot.infrastructure.persistence.entity.DeviceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class IotApplicationService {

    private final DeviceJpaRepository deviceRepository;
    private final SensorAlertJpaRepository alertRepository;
    private final IotAccessGuard accessGuard;
    private final IotReadingIngestionService ingestionService;

    public IotApplicationService(
            DeviceJpaRepository deviceRepository,
            SensorAlertJpaRepository alertRepository,
            IotAccessGuard accessGuard,
            IotReadingIngestionService ingestionService
    ) {
        this.deviceRepository = deviceRepository;
        this.alertRepository = alertRepository;
        this.accessGuard = accessGuard;
        this.ingestionService = ingestionService;
    }

    @Transactional
    public DeviceResponse registerDevice(RegisterDeviceRequest request) {
        accessGuard.requirePlot(request.plotId());
        String code = request.deviceCode().trim().toUpperCase();
        if (deviceRepository.existsByDeviceCodeIgnoreCase(code)) {
            throw new IotException("DEVICE_EXISTS", "Device code already registered", 409);
        }
        Instant now = Instant.now();
        DeviceEntity device = new DeviceEntity();
        device.setId(UUID.randomUUID());
        device.setDeviceCode(code);
        device.setPlotId(request.plotId());
        device.setName(request.name().trim());
        device.setStatus("ACTIVE");
        device.setCreatedAt(now);
        deviceRepository.save(device);
        return toDevice(device);
    }

    public IngestResultResponse ingest(IngestReadingRequest request) {
        DeviceEntity device = deviceRepository.findByDeviceCodeIgnoreCase(request.deviceCode().trim())
                .orElseThrow(() -> new IotException("DEVICE_NOT_FOUND", "Unknown device", 404));
        accessGuard.requirePlot(device.getPlotId());
        return ingestionService.ingest(request, device.getId());
    }

    public IngestResultResponse ingestFromMqtt(IngestReadingRequest request) {
        DeviceEntity device = deviceRepository.findByDeviceCodeIgnoreCase(request.deviceCode().trim())
                .orElseThrow(() -> new IotException("DEVICE_NOT_FOUND", "Unknown device", 404));
        return ingestionService.ingest(request, device.getId());
    }

    @Transactional(readOnly = true)
    public long openAlertCount(UUID deviceId) {
        return alertRepository.countByDeviceIdAndStatus(deviceId, "OPEN");
    }

    private static DeviceResponse toDevice(DeviceEntity d) {
        return new DeviceResponse(d.getId(), d.getDeviceCode(), d.getPlotId(), d.getName(), d.getStatus(), d.getLastSeenAt());
    }
}

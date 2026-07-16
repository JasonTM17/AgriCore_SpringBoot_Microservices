package com.agricore.iot.application.service;

import com.agricore.iot.api.request.IngestReadingRequest;
import com.agricore.iot.api.request.RegisterDeviceRequest;
import com.agricore.iot.api.response.DeviceResponse;
import com.agricore.iot.api.response.IngestResultResponse;
import com.agricore.iot.domain.exception.IotException;
import com.agricore.iot.infrastructure.persistence.*;
import com.agricore.iot.infrastructure.persistence.entity.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class IotApplicationService {

    private final DeviceJpaRepository deviceRepository;
    private final SensorReadingJpaRepository readingRepository;
    private final ThresholdRuleJpaRepository ruleRepository;
    private final SensorAlertJpaRepository alertRepository;
    private final long cooldownMinutes;

    public IotApplicationService(
            DeviceJpaRepository deviceRepository,
            SensorReadingJpaRepository readingRepository,
            ThresholdRuleJpaRepository ruleRepository,
            SensorAlertJpaRepository alertRepository,
            @Value("${agricore.alert.cooldown-minutes:15}") long cooldownMinutes
    ) {
        this.deviceRepository = deviceRepository;
        this.readingRepository = readingRepository;
        this.ruleRepository = ruleRepository;
        this.alertRepository = alertRepository;
        this.cooldownMinutes = cooldownMinutes;
    }

    @Transactional
    public DeviceResponse registerDevice(RegisterDeviceRequest request) {
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

    @Transactional
    public IngestResultResponse ingest(IngestReadingRequest request) {
        DeviceEntity device = deviceRepository.findByDeviceCodeIgnoreCase(request.deviceCode().trim())
                .orElseThrow(() -> new IotException("DEVICE_NOT_FOUND", "Unknown device", 404));

        Instant now = Instant.now();
        Instant recordedAt = request.recordedAt() == null ? now : request.recordedAt();
        device.setLastSeenAt(now);
        device.setStatus("ACTIVE");
        deviceRepository.save(device);

        SensorReadingEntity reading = new SensorReadingEntity();
        reading.setId(UUID.randomUUID());
        reading.setDeviceId(device.getId());
        reading.setMetricType(request.metricType().trim().toUpperCase());
        reading.setMetricValue(request.metricValue());
        reading.setUnit(request.unit().trim().toUpperCase());
        reading.setRecordedAt(recordedAt);
        reading.setCreatedAt(now);
        readingRepository.save(reading);

        List<ThresholdRuleEntity> rules = ruleRepository.findByMetricTypeAndActiveTrue(reading.getMetricType());
        for (ThresholdRuleEntity rule : rules) {
            boolean below = rule.getMinValue() != null && reading.getMetricValue().compareTo(rule.getMinValue()) < 0;
            boolean above = rule.getMaxValue() != null && reading.getMetricValue().compareTo(rule.getMaxValue()) > 0;
            if (!below && !above) {
                continue;
            }

            String fingerprint = device.getId() + ":" + reading.getMetricType() + ":v" + rule.getRuleVersion();
            var open = alertRepository.findFirstByFingerprintAndStatusOrderByCreatedAtDesc(fingerprint, "OPEN");
            if (open.isPresent()) {
                SensorAlertEntity existing = open.get();
                Instant cooldownDeadline = existing.getLastSeenAt().plus(cooldownMinutes, ChronoUnit.MINUTES);
                if (now.isBefore(cooldownDeadline)) {
                    existing.setLastSeenAt(now);
                    existing.setMetricValue(reading.getMetricValue());
                    alertRepository.save(existing);
                    return new IngestResultResponse(reading.getId(), false, existing.getId(), "OPEN",
                            "Alert suppressed by cooldown window");
                }
            }

            SensorAlertEntity alert = new SensorAlertEntity();
            alert.setId(UUID.randomUUID());
            alert.setDeviceId(device.getId());
            alert.setMetricType(reading.getMetricType());
            alert.setMetricValue(reading.getMetricValue());
            alert.setSeverity(rule.getSeverity());
            alert.setStatus("OPEN");
            alert.setRuleVersion(rule.getRuleVersion());
            alert.setFingerprint(fingerprint);
            alert.setMessage(below
                    ? reading.getMetricType() + " below minimum " + rule.getMinValue()
                    : reading.getMetricType() + " above maximum " + rule.getMaxValue());
            alert.setCreatedAt(now);
            alert.setLastSeenAt(now);
            alertRepository.save(alert);
            return new IngestResultResponse(reading.getId(), true, alert.getId(), "OPEN", alert.getMessage());
        }

        return new IngestResultResponse(reading.getId(), false, null, null, "Reading accepted");
    }

    @Transactional(readOnly = true)
    public long openAlertCount(UUID deviceId) {
        return alertRepository.countByDeviceIdAndStatus(deviceId, "OPEN");
    }

    private static DeviceResponse toDevice(DeviceEntity d) {
        return new DeviceResponse(d.getId(), d.getDeviceCode(), d.getPlotId(), d.getName(), d.getStatus(), d.getLastSeenAt());
    }
}

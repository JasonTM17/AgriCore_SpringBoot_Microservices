package com.agricore.iot.application.service;

import com.agricore.iot.api.request.IngestReadingRequest;
import com.agricore.iot.api.response.IngestResultResponse;
import com.agricore.iot.domain.exception.IotException;
import com.agricore.iot.infrastructure.persistence.DeviceJpaRepository;
import com.agricore.iot.infrastructure.persistence.SensorAlertJpaRepository;
import com.agricore.iot.infrastructure.persistence.SensorReadingJpaRepository;
import com.agricore.iot.infrastructure.persistence.ThresholdRuleJpaRepository;
import com.agricore.iot.infrastructure.persistence.entity.DeviceEntity;
import com.agricore.iot.infrastructure.persistence.entity.SensorAlertEntity;
import com.agricore.iot.infrastructure.persistence.entity.SensorReadingEntity;
import com.agricore.iot.infrastructure.persistence.entity.ThresholdRuleEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class IotReadingIngestionService {

    private final DeviceJpaRepository deviceRepository;
    private final SensorReadingJpaRepository readingRepository;
    private final ThresholdRuleJpaRepository ruleRepository;
    private final SensorAlertJpaRepository alertRepository;
    private final IotEventOutboxWriter eventWriter;
    private final IotMetrics metrics;
    private final long cooldownMinutes;

    public IotReadingIngestionService(
            DeviceJpaRepository deviceRepository,
            SensorReadingJpaRepository readingRepository,
            ThresholdRuleJpaRepository ruleRepository,
            SensorAlertJpaRepository alertRepository,
            IotEventOutboxWriter eventWriter,
            IotMetrics metrics,
            @Value("${agricore.alert.cooldown-minutes:15}") long cooldownMinutes
    ) {
        this.deviceRepository = deviceRepository;
        this.readingRepository = readingRepository;
        this.ruleRepository = ruleRepository;
        this.alertRepository = alertRepository;
        this.eventWriter = eventWriter;
        this.metrics = metrics;
        this.cooldownMinutes = cooldownMinutes;
    }

    @Transactional
    public IngestResultResponse ingest(IngestReadingRequest request, UUID authorizedDeviceId) {
        DeviceEntity device = deviceRepository.findByDeviceCodeIgnoreCaseForUpdate(request.deviceCode().trim())
                .filter(candidate -> candidate.getId().equals(authorizedDeviceId))
                .orElseThrow(() -> new IotException("DEVICE_NOT_FOUND", "Unknown device", 404));

        Instant now = Instant.now();
        Instant recordedAt = request.recordedAt() == null ? now : request.recordedAt();
        device.setLastSeenAt(now);
        device.setStatus("ACTIVE");
        deviceRepository.save(device);

        SensorReadingEntity reading = createReading(request, device, recordedAt, now);
        readingRepository.save(reading);
        eventWriter.sensorReadingReceived(device, reading);
        metrics.recordReading();

        List<ThresholdRuleEntity> rules = ruleRepository.findByMetricTypeAndActiveTrue(reading.getMetricType());
        for (ThresholdRuleEntity rule : rules) {
            IngestResultResponse result = evaluateThreshold(device, reading, rule, now);
            if (result != null) {
                return result;
            }
        }
        return new IngestResultResponse(reading.getId(), false, null, null, "Reading accepted");
    }

    private SensorReadingEntity createReading(
            IngestReadingRequest request,
            DeviceEntity device,
            Instant recordedAt,
            Instant now
    ) {
        SensorReadingEntity reading = new SensorReadingEntity();
        reading.setId(UUID.randomUUID());
        reading.setDeviceId(device.getId());
        reading.setMetricType(request.metricType().trim().toUpperCase());
        reading.setMetricValue(request.metricValue());
        reading.setUnit(request.unit().trim().toUpperCase());
        reading.setRecordedAt(recordedAt);
        reading.setCreatedAt(now);
        return reading;
    }

    private IngestResultResponse evaluateThreshold(
            DeviceEntity device,
            SensorReadingEntity reading,
            ThresholdRuleEntity rule,
            Instant now
    ) {
        boolean below = rule.getMinValue() != null && reading.getMetricValue().compareTo(rule.getMinValue()) < 0;
        boolean above = rule.getMaxValue() != null && reading.getMetricValue().compareTo(rule.getMaxValue()) > 0;
        if (!below && !above) {
            return null;
        }

        String fingerprint = device.getId() + ":" + reading.getMetricType() + ":v" + rule.getRuleVersion();
        var openAlert = alertRepository.findFirstByFingerprintAndStatusOrderByCreatedAtDesc(fingerprint, "OPEN");
        if (openAlert.isPresent()) {
            SensorAlertEntity existing = openAlert.get();
            Instant cooldownDeadline = existing.getLastSeenAt().plus(cooldownMinutes, ChronoUnit.MINUTES);
            if (now.isBefore(cooldownDeadline)) {
                existing.setLastSeenAt(now);
                existing.setMetricValue(reading.getMetricValue());
                alertRepository.save(existing);
                metrics.recordSuppressedAlert();
                return new IngestResultResponse(reading.getId(), false, existing.getId(), "OPEN",
                        "Alert suppressed by cooldown window");
            }
        }

        SensorAlertEntity alert = createAlert(device, reading, rule, fingerprint, below, now);
        alertRepository.save(alert);
        eventWriter.sensorThresholdExceeded(device, reading, rule, alert);
        metrics.recordCreatedAlert();
        return new IngestResultResponse(reading.getId(), true, alert.getId(), "OPEN", alert.getMessage());
    }

    private SensorAlertEntity createAlert(
            DeviceEntity device,
            SensorReadingEntity reading,
            ThresholdRuleEntity rule,
            String fingerprint,
            boolean below,
            Instant now
    ) {
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
        return alert;
    }
}

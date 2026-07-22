package com.agricore.iot;

import com.agricore.common.event.EventTypes;
import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.iot.api.request.IngestReadingRequest;
import com.agricore.iot.api.request.RegisterDeviceRequest;
import com.agricore.iot.api.response.DeviceResponse;
import com.agricore.iot.application.service.IotApplicationService;
import com.agricore.iot.application.service.IotDeviceStatusService;
import com.agricore.iot.infrastructure.persistence.DeviceJpaRepository;
import com.agricore.iot.infrastructure.persistence.OutboxJpaRepository;
import com.agricore.iot.infrastructure.persistence.entity.DeviceEntity;
import com.agricore.iot.infrastructure.persistence.entity.OutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class IotDeviceOfflineIntegrationTest {

    @Autowired
    private IotApplicationService applicationService;
    @Autowired
    private IotDeviceStatusService statusService;
    @Autowired
    private DeviceJpaRepository deviceRepository;
    @Autowired
    private OutboxJpaRepository outboxRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void emitsOncePerOfflineTransitionAndReactivatesOnNewReading() throws Exception {
        String deviceCode = ("OFFLINE-" + UUID.randomUUID()).toUpperCase();
        DeviceResponse registered = applicationService.registerDevice(new RegisterDeviceRequest(
                deviceCode,
                UUID.randomUUID(),
                "Remote weather station"
        ));
        makeDeviceStale(registered.id());

        assertThat(statusService.markOfflineDevices()).isEqualTo(1);
        assertThat(statusService.markOfflineDevices()).isZero();
        assertThat(deviceRepository.findById(registered.id()).orElseThrow().getStatus()).isEqualTo("OFFLINE");

        applicationService.ingest(new IngestReadingRequest(
                deviceCode,
                "AIR_TEMPERATURE",
                new BigDecimal("25.5000"),
                "C",
                Instant.now()
        ));
        assertThat(deviceRepository.findById(registered.id()).orElseThrow().getStatus()).isEqualTo("ACTIVE");

        makeDeviceStale(registered.id());
        assertThat(statusService.markOfflineDevices()).isEqualTo(1);

        List<OutboxEventEntity> offlineEvents = outboxRepository.findAll().stream()
                .filter(event -> EventTypes.DEVICE_OFFLINE_DETECTED.equals(event.getEventType()))
                .filter(event -> registered.id().toString().equals(event.getAggregateId()))
                .toList();
        assertThat(offlineEvents).hasSize(2);
        for (OutboxEventEntity event : offlineEvents) {
            JsonNode envelope = objectMapper.readTree(event.getPayload());
            assertThat(envelope.path("eventId").asText()).isEqualTo(event.getId().toString());
            assertThat(envelope.path("payload").path("deviceId").asText()).isEqualTo(registered.id().toString());
            assertThat(envelope.path("payload").path("offlineAfterSeconds").asLong()).isEqualTo(900);
        }
    }

    @Test
    void concurrentDetectorsEmitOnlyOneOfflineEvent() throws Exception {
        String deviceCode = ("CONCURRENT-OFFLINE-" + UUID.randomUUID()).toUpperCase();
        DeviceResponse registered = applicationService.registerDevice(new RegisterDeviceRequest(
                deviceCode,
                UUID.randomUUID(),
                "Concurrent weather station"
        ));
        makeDeviceStale(registered.id());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Integer> first = detectAfterBarrier(executor, ready, start);
            CompletableFuture<Integer> second = detectAfterBarrier(executor, ready, start);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }

        assertThat(offlineEventsFor(registered.id())).hasSize(1);
        assertThat(deviceRepository.findById(registered.id()).orElseThrow().getStatus()).isEqualTo("OFFLINE");
    }

    private CompletableFuture<Integer> detectAfterBarrier(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for detector start");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Detector interrupted", exception);
            }
            return statusService.markOfflineDevices();
        }, executor);
    }

    private List<OutboxEventEntity> offlineEventsFor(UUID deviceId) {
        return outboxRepository.findAll().stream()
                .filter(event -> EventTypes.DEVICE_OFFLINE_DETECTED.equals(event.getEventType()))
                .filter(event -> deviceId.toString().equals(event.getAggregateId()))
                .toList();
    }

    private void makeDeviceStale(UUID deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId).orElseThrow();
        Instant staleAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        if (device.getLastSeenAt() == null) {
            device.setCreatedAt(staleAt);
        } else {
            device.setLastSeenAt(staleAt);
        }
        deviceRepository.saveAndFlush(device);
    }
}

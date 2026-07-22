package com.agricore.iot.application.service;

import com.agricore.iot.infrastructure.persistence.SensorAlertJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IotMetricsTest {

    @Test
    void recordsIngestAndAlertOutcomesWithoutExternalValueTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SensorAlertJpaRepository repository = mock(SensorAlertJpaRepository.class);
        when(repository.countByStatus("OPEN")).thenReturn(3L);
        IotMetrics metrics = new IotMetrics(registry, repository);

        metrics.recordReading();
        metrics.recordCreatedAlert();
        metrics.recordSuppressedAlert();

        assertThat(registry.get("agricore.iot.readings").counter().count()).isEqualTo(1);
        assertThat(registry.get("agricore.iot.alerts").tag("outcome", "created").counter().count()).isEqualTo(1);
        assertThat(registry.get("agricore.iot.alerts").tag("outcome", "suppressed").counter().count()).isEqualTo(1);
        assertThat(registry.get("agricore.iot.open.alerts").gauge().value()).isEqualTo(3);
    }
}

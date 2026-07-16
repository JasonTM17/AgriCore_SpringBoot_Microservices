package com.agricore.iot.infrastructure.persistence;

import com.agricore.iot.infrastructure.persistence.entity.ThresholdRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ThresholdRuleJpaRepository extends JpaRepository<ThresholdRuleEntity, UUID> {
    List<ThresholdRuleEntity> findByMetricTypeAndActiveTrue(String metricType);
}
